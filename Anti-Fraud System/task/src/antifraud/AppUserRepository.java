package antifraud;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends CrudRepository<AppUser, Integer> {
    Optional<AppUser> findAppUserByUsernameIgnoreCase(String username);
    List<AppUser> findByOrderById();
    Optional<AppUser> findAllById(long id);



}