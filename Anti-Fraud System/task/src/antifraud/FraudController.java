package antifraud;

import jakarta.validation.Valid;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Validated
@RestController
public class FraudController {

    private final AppUserRepository repository;
    private final SusIpAddressRepository susIpAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final StolenCardRepository stolenCardRepository;

    public FraudController(AppUserRepository repository, SusIpAddressRepository susIpAddressRepository,
                           PasswordEncoder passwordEncoder, StolenCardRepository stolenCardRepository) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.stolenCardRepository = stolenCardRepository;
        this.susIpAddressRepository = susIpAddressRepository;
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
    public TransactionResponse postTransaction(@RequestBody @Valid TransactionRequest request) {

        long sum = request.getAmount();
        String number = request.getNumber();
        String ip = request.getIp();
        List<String> errors = new ArrayList<>();
        TransactionResponse response = new TransactionResponse();

        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValidInet4Address(ip)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Not valid IP");

        if (!LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Wrong card number format");

        if (susIpAddressRepository.existsByIp(ip)) errors.add("ip");
        if (stolenCardRepository.existsByNumber(number)) errors.add("card-number");

        if (sum <= 200) {
            errors.add("none");
            response.setResult("ALLOWED");
        } else if (sum <= 1500) {
            if (errors.contains("ip") || errors.contains("card-number")) {
                response.setResult("PROHIBITED");
            } else {
                response.setResult("MANUAL_PROCESSING");
                errors.add("amount");
            }
        } else {
            errors.add("amount");
            response.setResult("PROHIBITED");
        }


        String result = errors.stream().sorted().collect(Collectors.joining(", "));

        response.setInfo(result);

        return response;
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

    @PostMapping("/api/antifraud/suspicious-ip")
    public SusIpAddress postSusIp(@RequestBody SusIpRequest request) {


        SusIpAddress susAddress = new SusIpAddress();
        susAddress.setIp(request.ip());

        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValidInet4Address(susAddress.getIp())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Not valid IP");

        Optional<SusIpAddress> ipOptional = susIpAddressRepository.findSusIpAddressByIp(request.ip);
        if (ipOptional.isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ip already exists");

        susIpAddressRepository.save(susAddress);

        return susAddress;
    }

    @DeleteMapping("/api/antifraud/suspicious-ip/{ip}")
    public Map<String, String> deleteSusIp(@PathVariable String ip) {

        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValidInet4Address(ip)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Not valid IP");

        Optional<SusIpAddress> ipOptional = susIpAddressRepository.findSusIpAddressByIp(ip);
        if (ipOptional.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ip not found");

        susIpAddressRepository.delete(ipOptional.get());

        String result = "IP %s successfully removed!".formatted(ip);
        Map<String, String> response = new HashMap<>();
        response.put("status", result);
        return response;
    }
    @GetMapping("/api/antifraud/suspicious-ip")
    public List<SusIpAddress> getSusIpList() {
        return susIpAddressRepository.findAllByOrderById();
    }

    @PostMapping("/api/antifraud/stolencard")
    public StolenCard postStolenCard(@RequestBody StolenCardRequest request) {
        StolenCard stolenCard = new StolenCard();
        stolenCard.setNumber(request.number);

        Optional<StolenCard> cardOptional = stolenCardRepository.findByNumber(request.number);
        if (cardOptional.isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Number exists in database");
        if (!LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(request.number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Wrong card number format");

        stolenCardRepository.save(stolenCard);
        return stolenCard;
    }

    @DeleteMapping("/api/antifraud/stolencard/{number}")
    public Map<String, String> deleteStolenCard(@PathVariable String number) {


        if (!LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Wrong card number format");

        Optional<StolenCard> cardOptional = stolenCardRepository.findByNumber(number);
        if (cardOptional.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Card number not found");


        stolenCardRepository.delete(cardOptional.get());

        String result = "Card %s successfully removed!".formatted(number);
        Map<String, String> response = new HashMap<>();
        response.put("status", result);
        return response;
    }

    @GetMapping("/api/antifraud/stolencard")
    public List<StolenCard> getStolenCardList() {
        return stolenCardRepository.findAllByOrderById();
    }

    public record StolenCardRequest(String number) {}

    public record SusIpRequest(String ip) {}
}




