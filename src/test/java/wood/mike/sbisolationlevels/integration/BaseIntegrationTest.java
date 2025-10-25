package wood.mike.sbisolationlevels.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wood.mike.sbisolationlevels.SbIsolationLevelsApplication;
import wood.mike.sbisolationlevels.model.Person;
import wood.mike.sbisolationlevels.repository.PersonRepository;

@Testcontainers
@SpringBootTest(classes = SbIsolationLevelsApplication.class)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection // Spring Boot 3.1+ feature: auto-configures the datasource URL, username, and password
    protected static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.35");
    
    protected static final long BOB_ID = 1L;
    protected static final String ORIGINAL_NAME = "bob";
    protected static final String NEW_NAME = "ALICE"; // Tx B's update

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired
    protected PersonRepository personRepository;

    @BeforeEach
    void setup() {
        Person bob = personRepository.findById(BOB_ID).orElseThrow();
        if (!ORIGINAL_NAME.equals(bob.getForename())) {
            bob.setForename(ORIGINAL_NAME);
            personRepository.save(bob);
        }
    }
}