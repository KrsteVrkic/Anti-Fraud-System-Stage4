package antifraud;

import jakarta.validation.Valid;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@RestController
public class FraudController {

    private final AppUserRepository repository;
    private final SusIpAddressRepository susIpAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final StolenCardRepository stolenCardRepository;
    private final TransactionRepository transactionRepository;
    private final List<String> regionCodex;

    public FraudController(AppUserRepository repository, SusIpAddressRepository susIpAddressRepository,
                           PasswordEncoder passwordEncoder, StolenCardRepository stolenCardRepository,
                           TransactionRepository transactionRepository, List<String> regionCodex) {
        this.transactionRepository = transactionRepository;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.stolenCardRepository = stolenCardRepository;
        this.susIpAddressRepository = susIpAddressRepository;
        this.regionCodex = List.of("EAP", "ECA", "HIC", "LAC", "MENA", "SA", "SSA");
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
        String region = request.getRegion();
        LocalDateTime date = request.getDate();
        var start = date.minusHours(1);
        String status = "why dont you initialize me";

        List<String> errors = new ArrayList<>();
        Transaction transaction = new Transaction();
        TransactionResponse response = new TransactionResponse();

        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValidInet4Address(ip)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Wrong ip format");

        if (!LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Wrong card number format");

        if (!regionCodex.contains(region)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid region format");

        if (isDateValid(date)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid date format");

        if (transactionRepository.countDistinctRegions(region, number, start, date) > 2) {
            errors.add("region-correlation");
            status = "PROHIBITED";
        }

        if (transactionRepository.countUniqueIp(ip, start, date) > 2) {
            errors.add("ip-correlation");
            status = "PROHIBITED";
        }

        if (transactionRepository.countDistinctRegions(region, number, start, date) == 2) {
            errors.add("region-correlation");
            status = "MANUAL_PROCESSING";
        }

        if (transactionRepository.countUniqueIp(ip, start, date) == 2) {
            errors.add("ip-correlation");
            status = "MANUAL_PROCESSING";
        }

        if (susIpAddressRepository.existsByIp(ip)) errors.add("ip");
        if (stolenCardRepository.existsByNumber(number)) errors.add("card-number");

        if (errors.isEmpty() && sum <= 200) {
            errors.add("none");
            status = "ALLOWED";
        } else if (sum <= 1500) {
            if (errors.contains("ip") || errors.contains("card-number")) {
                status = "PROHIBITED";
            } else {
                if (!status.equals("PROHIBITED")) {
                    status = "MANUAL_PROCESSING";
                }
                if (errors.isEmpty()) {
                    errors.add("amount");
                }
            }
        } else {
            errors.add("amount");
            status = "PROHIBITED";
        }

        transaction.setAmount(sum);
        transaction.setIp(ip);
        transaction.setNumber(number);
        transaction.setRegion(region);
        transaction.setDate(date);

        transactionRepository.save(transaction);
        String result = errors.stream().sorted().collect(Collectors.joining(", "));
        response.setInfo(result);
        response.setResult(status);
        return response;
    }

    public boolean isDateValid(LocalDateTime date) {

        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        try {
            LocalDateTime parseDate = LocalDateTime.parse(date.toString(), format);
            return true;
        } catch (Exception e) {
            return false;
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

    @PostMapping("/api/antifraud/suspicious-ip")
    public SusIpAddress postSusIp(@RequestBody SusIpRequest request) {


        SusIpAddress susAddress = new SusIpAddress();
        susAddress.setIp(request.ip());

        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValidInet4Address(susAddress.getIp()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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
        if (!LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(request.number))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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

    public record StolenCardRequest(String number) {
    }

    public record SusIpRequest(String ip) {
    }
}




