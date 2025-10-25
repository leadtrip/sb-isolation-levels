package wood.mike.sbisolationlevels.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import wood.mike.sbisolationlevels.model.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ReadCommittedIntegrationTest extends BaseIntegrationTest {

    private TransactionTemplate writerTemplate;
    private TransactionTemplate readerTemplate;


    @BeforeEach
    void setup() {
        // the writer template's isolation is not relevant and can stay as default
        writerTemplate = new TransactionTemplate(transactionManager);

        readerTemplate = new TransactionTemplate(transactionManager);
        readerTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void testNonRepeatableReadOccurs_ReadCommitted() throws Exception {
        System.out.println("\n--- Starting Non-Repeatable Read Test (READ_COMMITTED) ---");

        // Latch 1: Tx A waits after its FIRST read.
        CountDownLatch read1Latch = new CountDownLatch(1);
        // Latch 2: Tx B waits after its COMMIT.
        CountDownLatch commitLatch = new CountDownLatch(1);


        // 1. Transaction A (The Reader) - Runs in a separate thread
        Future<List<String>> readerFuture = Executors.newSingleThreadExecutor().submit(() -> {
            return readerTemplate.execute(status -> {
                try {
                    // List to store the two reads
                    List<String> reads = new ArrayList<>();

                    System.out.println("Tx A: Starting READ_COMMITTED transaction.");

                    // FIRST READ (Should see INITIAL_NAME)
                    String name1 = personRepository.findById(BOB_ID).orElseThrow().getForename();
                    reads.add(name1);
                    System.out.println("Tx A: First read: " + name1);

                    // Signal the writer (Tx B) to perform and commit its change
                    read1Latch.countDown();

                    // Wait for the writer (Tx B) to commit its change
                    commitLatch.await();

                    // Explicitly refresh the entity to bypass L1 cache
                    Person person = personRepository.findById(BOB_ID).orElseThrow();
                    entityManager.refresh(person); // Forces a database re-read

                    // SECOND READ (This is where the anomaly occurs)
                    String name2 = person.getForename(); // Get the name from the refreshed entity
                    reads.add(name2);
                    System.out.println("Tx A: Second read: " + name2);

                    return reads;
                } catch (Exception e) {
                    e.printStackTrace();
                    status.setRollbackOnly(); // Rollback on error
                    return List.of("ERROR");
                }
            });
        });

        // 2. Transaction B (The Writer) - Runs in the main thread's explicit transaction
        read1Latch.await(5, TimeUnit.SECONDS); // Wait for Tx A to complete its first read.

        writerTemplate.executeWithoutResult(status -> {
            // Update the name
            Person person = personRepository.findById(BOB_ID).orElseThrow();
            person.setForename(NEW_NAME);
            personRepository.saveAndFlush(person);
            // This transaction will commit automatically when executeWithoutResult finishes
        });

        System.out.println("Tx B: Updated name and COMMITTED: " + NEW_NAME);

        commitLatch.countDown(); // Signal Tx A to perform its second read.

        // 3. Verification
        List<String> results = readerFuture.get(5, TimeUnit.SECONDS);

        // ASSERTION 1: Tx A's first read is correct
        assertThat(results.get(0))
                .as("Tx A's first read should see the initial committed value.")
                .isEqualTo(ORIGINAL_NAME);

        // ASSERTION 2: Non-Repeatable Read ANOMALY
        // In READ_COMMITTED, Tx A should see the change from committed Tx B.
        assertThat(results.get(1))
                .as("In READ_COMMITTED, Tx A should see Tx B's committed change on the second read.")
                .isEqualTo(NEW_NAME);

        System.out.println("--- Test Complete: Non-Repeatable Read Confirmed ---");
    }
}
