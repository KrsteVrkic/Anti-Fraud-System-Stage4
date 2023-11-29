package antifraud;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    @Query("SELECT COUNT(DISTINCT t.region) FROM Transaction t WHERE t.region <> ?1 AND t.number = ?2 AND t.date BETWEEN ?3 AND ?4")
    long countDistinctRegions(String region, String number, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT t.ip) FROM Transaction t WHERE t.ip <> ?1 AND t.date BETWEEN ?2 AND ?3")
    long countUniqueIp(String ip, LocalDateTime start, LocalDateTime end);

    List<Transaction> findAllByOrderById();
    List<Transaction> findByNumber(String number);

    List<Transaction> findByNumberOrderByDate(String number);

}
