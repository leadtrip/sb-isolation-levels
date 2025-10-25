package wood.mike.sbisolationlevels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wood.mike.sbisolationlevels.model.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
