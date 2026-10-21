package com.soukly.listing.perf;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Local performance data set of the search (CATA-01): 100,000 realistic PUBLISHED listings (cars with their
 * attributes, apartments, services "on quote", other subcategories; French and Arabic titles; every governorate;
 * 1 to 4 photos each) owned by one fixed account. Only with the {@code perf-seed} profile, and only on a local
 * database: it refuses any other host. Run once; a second start finds the listings and does nothing.
 *
 * <pre>./mvnw -pl listing-service spring-boot:run -Dspring-boot.run.profiles=perf-seed</pre>
 */
@Component
@Profile("perf-seed")
class PerfSeed implements ApplicationRunner {

    /** Owner of every generated listing, so that they can be told apart and removed. */
    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-00000000cafe");
    static final int COUNT = 100_000;

    private static final Logger log = LoggerFactory.getLogger(PerfSeed.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private static final String LISTINGS = """
            WITH refs AS (
                SELECT (SELECT array_agg(code ORDER BY code) FROM governorates) AS governorates,
                       (SELECT array_agg(c.code ORDER BY c.code) FROM categories c
                        WHERE c.parent_code IS NOT NULL AND c.active
                          AND c.parent_code NOT IN ('services', 'jobs')
                          AND NOT EXISTS (SELECT 1 FROM category_attributes a WHERE a.category_code = c.code))
                           AS plain_categories),
            seed AS (
                SELECT n, random() AS r, now() - random() * interval '59 days' AS published_at,
                       refs.governorates[1 + floor(random() * cardinality(refs.governorates))::int] AS governorate,
                       refs.plain_categories[1 + floor(random() * cardinality(refs.plain_categories))::int]
                           AS plain_category,
                       (ARRAY['Peugeot', 'Renault', 'Volkswagen', 'Kia', 'Hyundai', 'Toyota', 'Citroën', 'Fiat',
                              'Mercedes', 'Seat'])[1 + floor(random() * 10)::int] AS brand,
                       (ARRAY['208', 'Clio', 'Golf', 'Rio', 'i20', 'Yaris', 'C3', 'Punto', 'Classe C', 'Ibiza'])
                           [1 + floor(random() * 10)::int] AS model,
                       2005 + floor(random() * 21)::int AS year,
                       (ARRAY['petrol', 'diesel', 'hybrid', 'electric', 'lpg'])[1 + floor(random() * 5)::int]
                           AS fuel,
                       (ARRAY['Canapé', 'Table basse', 'Réfrigérateur', 'Machine à laver', 'Téléphone',
                              'Ordinateur portable', 'Vélo', 'Armoire', 'Climatiseur', 'Télévision', 'هاتف جوال',
                              'ثلاجة', 'دراجة هوائية', 'خزانة ملابس', 'حاسوب محمول', 'مكيف هواء'])
                           [1 + floor(random() * 16)::int] AS item,
                       (ARRAY['très bon état', 'comme neuf', 'peu servi', 'à saisir', 'بحالة جيدة', 'جديد'])
                           [1 + floor(random() * 6)::int] AS state
                FROM generate_series(1, :count) AS n, refs)
            INSERT INTO listings (id, owner_id, status, category_code, title, description, price_mode, price_tnd,
                    negotiable, governorate_code, delegation_code, attributes, version, created_at, updated_at,
                    published_at, expires_at)
            SELECT gen_random_uuid(), :owner, 'PUBLISHED',
                   CASE WHEN n % 10 < 3 THEN 'vehicles.cars'
                        WHEN n % 10 < 5 THEN 'real-estate.apartments'
                        WHEN n % 10 = 5 THEN 'services.home-repair'
                        ELSE plain_category END,
                   CASE WHEN n % 10 < 3 THEN brand || ' ' || model || ' ' || year
                        WHEN n % 10 = 3 THEN 'Appartement S+' || (1 + n % 4) || ' à louer'
                        WHEN n % 10 = 4 THEN 'شقة للكراء S+' || (1 + n % 4)
                        WHEN n % 10 = 5 THEN 'Plombier et électricien, intervention rapide'
                        ELSE item || ' ' || state END,
                   'Annonce générée pour mesurer la recherche, ' || state || '. Disponible rapidement, '
                       || 'visible sur rendez-vous. إعلان للتجربة ' || n,
                   CASE WHEN n % 10 = 5 THEN 'ON_QUOTE' ELSE 'FIXED' END,
                   CASE WHEN n % 10 < 3 THEN 15000 + floor(r * 85000)::bigint
                        WHEN n % 10 < 5 THEN 300 + floor(r * 2700)::bigint
                        WHEN n % 10 = 5 THEN NULL
                        ELSE 10 + floor(r * 3000)::bigint END,
                   r < 0.5, governorate, NULL,
                   CASE WHEN n % 10 < 3 THEN jsonb_build_object('brand', brand, 'model', model, 'year', year,
                            'mileage-km', floor(r * 300000)::int, 'fuel', fuel,
                            'gearbox', CASE WHEN r < 0.7 THEN 'manual' ELSE 'automatic' END)
                        WHEN n % 10 < 5 THEN jsonb_build_object('transaction', 'rent',
                            'surface-m2', 50 + floor(r * 150)::int, 'rooms', 1 + n % 4, 'furnished', r < 0.3)
                        ELSE '{}'::jsonb END,
                   2, published_at, published_at, published_at, published_at + interval '60 days'
            FROM seed""";

    private static final String PHOTOS = """
            INSERT INTO listing_photos (id, listing_id, status, position, width, height, bytes, created_at,
                    updated_at)
            SELECT gen_random_uuid(), l.id, 'READY', p, 1600, 1200, 400000, l.published_at, l.published_at
            FROM listings l
            CROSS JOIN LATERAL generate_series(0, abs(hashtext(l.id::text)) % 4) AS p
            WHERE l.owner_id = :owner""";

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final DataSource dataSource;

    PerfSeed(JdbcClient jdbc, TransactionTemplate transactions, DataSource dataSource) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireLocalDatabase();
        long existing = jdbc.sql("SELECT count(*) FROM listings WHERE owner_id = :owner").param("owner", OWNER)
                .query(Long.class).single();
        if (existing > 0) {
            log.info("perf-seed: {} generated listings already present, nothing to do", existing);
            return;
        }
        long start = System.nanoTime();
        transactions.executeWithoutResult(status -> {
            jdbc.sql("SELECT setseed(0.42)").query((rs, row) -> 1).single();
            jdbc.sql(LISTINGS).param("count", COUNT).param("owner", OWNER).update();
            jdbc.sql(PHOTOS).param("owner", OWNER).update();
        });
        jdbc.sql("ANALYZE listings").update();
        jdbc.sql("ANALYZE listing_photos").update();
        log.info("perf-seed: {} published listings generated in {} s", COUNT, (System.nanoTime() - start) / 1e9);
    }

    private void requireLocalDatabase() {
        HikariDataSource hikari = DataSourceUnwrapper.unwrap(dataSource, HikariDataSource.class);
        String url = hikari == null ? "" : hikari.getJdbcUrl();
        String host = URI.create(url.replaceFirst("^jdbc:", "")).getHost();
        if (host == null || !LOCAL_HOSTS.contains(host)) {
            throw new IllegalStateException("perf-seed only runs on a local database, not on " + host);
        }
    }
}
