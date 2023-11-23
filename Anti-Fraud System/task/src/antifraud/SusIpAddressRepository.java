package antifraud;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface SusIpAddressRepository extends CrudRepository<SusIpAddress, Integer> {

    Optional<SusIpAddress> findSusIpAddressByIp(Long ip);

}
