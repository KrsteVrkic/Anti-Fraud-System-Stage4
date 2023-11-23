package antifraud;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Validated
@RestController
public class FraudController {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public FraudController(AppUserRepository repository,
                           PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping(path = "/api/auth/user")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse postRegistration(@RequestBody @Valid RegistrationRequest request) {

        var doesUserExist = repository.findAppUserByUsernameIgnoreCase(request.getUsername());
        if (doesUserExist.isPresent()) throw new userAlreadyExists();

        var user = new AppUser();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        if ((repository.findByOrderById().isEmpty())) {
            user.setAuthority("ADMINISTRATOR");
            user.setLocked(false);
        } else {
            user.setAuthority("MERCHANT");
        }
        repository.save(user);

        return new RegistrationResponse(user.getId(), request.getName(),
                request.getUsername(), user.getAuthority());

    }

    @GetMapping("/api/auth/list")
    public List<RegistrationResponse> getListUsers() {

        return repository.findByOrderById().stream().map(e -> new RegistrationResponse(e.getId(),
                e.getName(), e.getUsername(), e.getAuthority())).toList();

    }

    @PostMapping("/api/antifraud/transaction")
    public TransactionResponse postTransaction(@RequestBody TransactionRequest request) {

        long sum = request.getAmount();
        if (sum < 1) throw new InvalidAmount();

        if (sum <= 200) {
            return new TransactionResponse("ALLOWED");
        } else if (sum <= 1500) {
            return new TransactionResponse("MANUAL_PROCESSING");
        } else {
            return new TransactionResponse("PROHIBITED");
        }
    }

    @DeleteMapping("/api/auth/user/{username}")
    public DeleteResponse deleteUser(@PathVariable String username) {
        Optional<AppUser> userOptional = repository.findAppUserByUsernameIgnoreCase(username);

        if (userOptional.isEmpty()) {
            throw new UserNotFound();
        }

        AppUser user = userOptional.get();
        repository.delete(user);

        return new DeleteResponse(username, "Deleted successfully!");
    }

    @PutMapping("/api/auth/role")
    public RegistrationResponse putRole(@RequestBody RoleRequest request) {

        String username = request.getUsername();
        String role = request.getRole();

        Optional<AppUser> userOptional = repository.findAppUserByUsernameIgnoreCase(username);

        if (userOptional.isEmpty()) throw new UserNotFound();
        if (!role.equals("SUPPORT") && !role.equals("MERCHANT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
        AppUser user = userOptional.get();
        if (user.getAuthority().contains(role)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Role already assigned to user");

        user.setAuthority(role);
        repository.save(user);

        return new RegistrationResponse(user.getId(), user.getName(),
                user.getUsername(), user.getAuthority());
    }

    @PutMapping("/api/auth/access")
    public AccessResponse putAccess(@RequestBody AccessRequest request) {

        String username = request.getUsername();
        String operation = request.getOperation();
        String result;

        Optional<AppUser> userOptional = repository.findAppUserByUsernameIgnoreCase(username);

        if (userOptional.isEmpty()) throw new UserNotFound();

        AppUser user = userOptional.get();

        if (user.getAuthority().equals("ADMINISTRATOR")) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Admin cannot be blocked");

        if ("LOCK".equals(operation)) {
            user.setLocked(true);
            result = "locked";
        } else if ("UNLOCK".equals(operation)) {
            user.setLocked(false);
            result = "unlocked";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operation");
        }

        repository.save(user);
        String response = "User %s %s!".formatted(username, result);
        return new AccessResponse(response);
    }

    /*@PostMapping("/api/antifraud/suspicious-id")
    public List<Ip> postSusIp(@RequestBody SusRequest ip) {

    }

    InetAddressValidator validator = InetAddressValidator.getInstance();
    validator.isValidInet4Address(ipAddress);
    LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(cardNumber);*/



}




