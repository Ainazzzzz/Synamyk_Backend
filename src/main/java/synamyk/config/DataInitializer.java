package synamyk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import synamyk.entities.ProductPrice;
import synamyk.entities.Region;
import synamyk.entities.User;
import synamyk.enums.ProductCode;
import synamyk.enums.Role;
import synamyk.repo.ProductPriceRepository;
import synamyk.repo.RegionRepository;
import synamyk.repo.UserRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.phone:+996700000000}")
    private String adminPhone;

    @Value("${admin.password:Admin1234!}")
    private String adminPassword;

    @Value("${admin.first-name:Admin}")
    private String adminFirstName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initRegions();
        initAdmin();
        initProducts();
    }

    // ===== REGIONS =====

    private static final List<String[]> REGIONS = List.of(
            // { nameRu, nameKy }
            new String[]{"Бишкек",                  "Бишкек"},
            new String[]{"Ош",                       "Ош"},
            new String[]{"Чуйская область",          "Чүй облусу"},
            new String[]{"Иссык-Кульская область",   "Ысык-Көл облусу"},
            new String[]{"Нарынская область",        "Нарын облусу"},
            new String[]{"Таласская область",        "Талас облусу"},
            new String[]{"Джалал-Абадская область",  "Жалал-Абад облусу"},
            new String[]{"Ошская область",           "Ош облусу"},
            new String[]{"Баткенская область",       "Баткен облусу"}
    );

    private void initRegions() {
        int created = 0;
        for (String[] r : REGIONS) {
            String nameRu = r[0];
            String nameKy = r[1];
            if (!regionRepository.existsByName(nameRu)) {
                regionRepository.save(Region.builder()
                        .name(nameRu)
                        .nameKy(nameKy)
                        .build());
                created++;
            } else {
                // Ensure nameKy is filled even if row existed without it
                regionRepository.findAll().stream()
                        .filter(region -> nameRu.equals(region.getName()) && region.getNameKy() == null)
                        .forEach(region -> {
                            region.setNameKy(nameKy);
                            regionRepository.save(region);
                        });
            }
        }
        if (created > 0) log.info("Regions seeded: {} created", created);
    }

    // ===== PRODUCTS =====

    /** Catalogue products start inactive with price 0 — the admin sets a price and enables them. */
    private void initProducts() {
        for (ProductCode code : ProductCode.values()) {
            if (productPriceRepository.findByCode(code).isEmpty()) {
                productPriceRepository.save(ProductPrice.builder().code(code).build());
                log.info("Product seeded: {}", code);
            }
        }
    }

    // ===== ADMIN =====

    private void initAdmin() {
        String phone = normalizePhone(adminPhone);
        if (userRepository.existsByPhone(phone)) {
            log.debug("Admin already exists: {}", phone);
            return;
        }
        User admin = User.builder()
                .phone(phone)
                .password(passwordEncoder.encode(adminPassword))
                .firstName(adminFirstName)
                .role(Role.ADMIN)
                .phoneVerified(true)
                .active(true)
                .language("RU")
                .build();
        userRepository.save(admin);
        log.info("Admin account created: {}", phone);
    }

    private String normalizePhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("0")) cleaned = "996" + cleaned.substring(1);
        if (!cleaned.startsWith("996")) cleaned = "996" + cleaned;
        return cleaned;
    }
}
