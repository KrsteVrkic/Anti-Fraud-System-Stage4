package antifraud;

import jakarta.transaction.Transactional;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface SusIpAddressRepository extends CrudRepository<SusIpAddress, Integer> {

    Optional<SusIpAddress> findSusIpAddressByIp(String ip);

    List<SusIpAddress> findAllByOrderById();

    boolean existsByIp(String ip);


}
