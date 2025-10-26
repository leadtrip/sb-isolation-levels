package wood.mike.sbisolationlevels.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import wood.mike.sbisolationlevels.model.Person;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In this test we set the isolation level on the reader thread/transaction to ISOLATION_READ_UNCOMMITTED.
 * Here, dirty reads, non-repeatable reads, and phantom reads can occur.
 * This level allows a row changed by one transaction to be read by another transaction before any changes in that row
 * have been committed (a "dirty read").
 * If any of the changes are rolled back, the second transaction will have retrieved an invalid row.
 */
public class ReadUncommittedIntegrationTest extends BaseIntegrationTest {

    private TransactionTemplate writerTemplate;
    private TransactionTemplate readerTemplate;

    @BeforeEach
    void setup() {
        // the writer template's isolation is not relevant and can stay as default
        writerTemplate = new TransactionTemplate(transactionManager);

        readerTemplate = new TransactionTemplate(transactionManager);
        readerTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
    }

    @Test
    void testDirtyReadOccurs_ReadUncommitted() throws Exception {
        System.out.println("\n--- Starting Dirty Read Test (READ_UNCOMMITTED) ---");

        CountDownLatch pauseLatch = new CountDownLatch(1);
        CountDownLatch readLatch = new CountDownLatch(1);

        // 1. Transaction A (The Writer) - Runs in a separate thread
        Future<Void> writerFuture = Executors.newSingleThreadExecutor().submit(() -> {
            writerTemplate.execute(status -> {
                try {
                    System.out.println("Tx A: Starting UNCOMMITTED update.");

                    // Update and save creating a dirty change
                    Person person = personRepository.findById(BOB_ID).orElseThrow();
                    person.setForename(NEW_NAME);
                    personRepository.save(person);
                    personRepository.flush();

                    System.out.println("Tx A: Updated person, now pausing before rollback.");

                    // Signal Tx B to read, and wait for the read to complete
                    pauseLatch.countDown();
                    readLatch.await();

                    // Mark for rollback so we can test dirty read effects
                    status.setRollbackOnly();
                    System.out.println("Tx A: Rolling back.");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });
            return null;
        });

        // Wait for Tx A to signal the update is done and it is paused
        pauseLatch.await(5, TimeUnit.SECONDS);


        // 2. Transaction B (The Reader) - Runs in the main thread's explicit transaction
        String nameReadByTxB = readerTemplate.execute(status -> {
            String name = personRepository.findById(BOB_ID)
                    .map(Person::getForename)
                    .orElse("NOT_FOUND");
            System.out.println("Tx B: Read name: " + name);
            return name;
        });

        readLatch.countDown(); // Signal Tx A to finish its rollback

        System.out.println("Tx B: ASSERTING read value...");

        // ASSERTION: Tx B *must* see the uncommitted (DIRTY) value
        assertThat(nameReadByTxB)
                .as("In READ_UNCOMMITTED, Tx B should see the uncommitted change from Tx A.")
                .isEqualTo(NEW_NAME);

        // 3. Rollback Tx A
        writerFuture.get(2, TimeUnit.SECONDS); // Wait for writer to finish

        // 4. Verification: Check the final committed state
        String finalName = personRepository.findById(BOB_ID)
                .map(Person::getForename)
                .orElse("NOT_FOUND");
        System.out.println("Final state check (after rollback): " + finalName);

        // Since Tx A rolled back, the name should revert to the original value
        assertThat(finalName)
                .as("After Tx A rolls back, the final committed value should be the original name.")
                .isEqualTo(ORIGINAL_NAME);

        System.out.println("--- Test Complete: Dirty Read Confirmed ---");
    }
}
