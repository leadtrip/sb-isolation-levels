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

/**
 * In this test we set the isolation level on the reader thread/transaction to ISOLATION_REPEATABLE_READ.
 * Here, dirty reads and non-repeatable reads are prevented; phantom reads can occur.
 * This level prohibits a transaction from reading a row with uncommitted changes in it,
 * and it also prohibits the situation where one transaction reads a row, a second transaction alters the row,
 * and the first transaction re-reads the row, getting different values the second time (a "non-repeatable read").
 */
public class RepeatableReadIntegrationTest extends BaseIntegrationTest {

    private TransactionTemplate writerTemplate;
    private TransactionTemplate readerTemplate;

    @BeforeEach
    void setup() {
        // the writer template's isolation is not relevant and can stay as default
        writerTemplate = new TransactionTemplate(transactionManager);

        readerTemplate = new TransactionTemplate(transactionManager);
        readerTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Test
    void testNonRepeatableReadPrevented_RepeatableRead() throws Exception {
        System.out.println("\n--- Starting REPEATABLE_READ Test ---");

        // Latch 1: Tx A waits after its FIRST read.
        CountDownLatch read1Latch = new CountDownLatch(1);
        // Latch 2: Tx B waits after its COMMIT.
        CountDownLatch commitLatch = new CountDownLatch(1);

        // 1. Transaction A (The Reader) - Runs in a separate thread
        Future<List<String>> readerFuture = Executors.newSingleThreadExecutor().submit(() -> {
            return readerTemplate.execute(status -> {
                try {
                    List<String> reads = new ArrayList<>();

                    System.out.println("Tx A: Starting REPEATABLE_READ transaction.");

                    // FIRST READ (Should see INITIAL_NAME)
                    Person person1 = personRepository.findById(BOB_ID).orElseThrow();
                    reads.add(person1.getForename());
                    System.out.println("Tx A: First read: " + person1.getForename());

                    // Signal the writer (Tx B) to perform and commit its change
                    read1Latch.countDown();

                    // Wait for the writer (Tx B) to commit its change
                    commitLatch.await();

                    // Force cache refresh before second read
                    entityManager.clear(); // Clear L1 cache

                    // SECOND READ (This is where the anomaly is PREVENTED)
                    Person person2 = personRepository.findById(BOB_ID).orElseThrow();
                    reads.add(person2.getForename());
                    System.out.println("Tx A: Second read: " + person2.getForename());

                    return reads;
                } catch (Exception e) {
                    return List.of("ERROR");
                }
            });
        });

        // 2. Transaction B (The Writer) - Runs in the main thread (uses default/lower isolation)
        read1Latch.await(5, TimeUnit.SECONDS); // Wait for Tx A's first read.

        writerTemplate.executeWithoutResult(status -> {
            Person person = personRepository.findById(BOB_ID).orElseThrow();
            person.setForename(NEW_NAME);
            personRepository.saveAndFlush(person);
            // Transaction B commits here.
        });

        System.out.println("Tx B: Updated name and COMMITTED: " + NEW_NAME);

        commitLatch.countDown(); // Signal Tx A to perform its second read.

        // 3. Verification
        List<String> results = readerFuture.get(5, TimeUnit.SECONDS);

        // ASSERTION 1: Tx A's first read is correct
        assertThat(results.get(0))
                .as("Tx A's first read should see the initial committed value.")
                .isEqualTo(ORIGINAL_NAME);

        // ASSERTION 2: Non-Repeatable Read IS PREVENTED
        // In REPEATABLE_READ, Tx A must NOT see the change committed by Tx B.
        assertThat(results.get(1))
                .as("In REPEATABLE_READ, Tx A must NOT see Tx B's committed change; the read must be stable.")
                .isEqualTo(ORIGINAL_NAME); // Expect the original value!

        System.out.println("--- Test Complete: Non-Repeatable Read Prevented ---");
    }
}
