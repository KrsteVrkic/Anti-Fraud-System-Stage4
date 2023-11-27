package antifraud;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface StolenCardRepository extends CrudRepository<StolenCard, Integer> {

    Optional<StolenCard> findByNumber(String number);

    List<StolenCard> findAllByOrderById();

    boolean existsByNumber(String number);
}
