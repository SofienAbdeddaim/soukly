package com.soukly.social.post;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.social.caption.CaptionBuilder;
import com.soukly.social.caption.ReferentialLabels;
import com.soukly.social.connection.ConnectionHealth;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.facebook.FacebookGraphClient;
import com.soukly.social.facebook.FacebookProperties;
import com.soukly.social.instagram.InstagramGraphClient;
import com.soukly.social.instagram.InstagramProperties;
import com.soukly.social.photo.PhotoVariantService;

import tools.jackson.databind.json.JsonMapper;

/**
 * The posts of each {@link PostChannel}: a repository and a publisher per network. Those of the seller (SOC-04,
 * SOC-05) are the primary beans; those of Soukly's own Page and Instagram account (SOC-07) are qualified
 * {@value #HOUSE}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({ FacebookPostingProperties.class, InstagramPostingProperties.class,
        HouseProperties.class })
class PostChannelsConfig {

    static final String HOUSE = "house";

    @Bean
    @Primary
    SocialPostRepository socialPostRepository(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        return new SocialPostRepository(jdbc, json, clock, PostChannel.SELLER);
    }

    @Bean
    @Qualifier(HOUSE)
    SocialPostRepository housePostRepository(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        return new SocialPostRepository(jdbc, json, clock, PostChannel.HOUSE);
    }

    @Bean
    @Primary
    FacebookPublisher facebookPublisher(SocialPostRepository socialPostRepository, HouseProperties house,
            SocialConnectionRepository connections, ConnectionHealth connectionHealth, FacebookGraphClient facebook,
            FacebookProperties facebookProperties, FacebookPostingProperties properties, CaptionBuilder captions,
            ReferentialLabels labels, PostMetrics metrics, TransactionTemplate transactions, Clock clock) {
        return new FacebookPublisher(socialPostRepository, house, connections, connectionHealth, facebook,
                facebookProperties, properties, captions, labels, metrics, transactions, clock);
    }

    @Bean
    @Qualifier(HOUSE)
    FacebookPublisher houseFacebookPublisher(@Qualifier(HOUSE) SocialPostRepository housePostRepository,
            HouseProperties house, SocialConnectionRepository connections, ConnectionHealth connectionHealth,
            FacebookGraphClient facebook, FacebookProperties facebookProperties, FacebookPostingProperties properties,
            CaptionBuilder captions, ReferentialLabels labels, PostMetrics metrics, TransactionTemplate transactions,
            Clock clock) {
        return new FacebookPublisher(housePostRepository, house, connections, connectionHealth, facebook,
                facebookProperties, properties, captions, labels, metrics, transactions, clock);
    }

    @Bean
    @Primary
    InstagramPublisher instagramPublisher(SocialPostRepository socialPostRepository, HouseProperties house,
            SocialConnectionRepository connections, ConnectionHealth connectionHealth, InstagramGraphClient instagram,
            InstagramProperties instagramProperties, InstagramPostingProperties properties, CaptionBuilder captions,
            ReferentialLabels labels, PhotoVariantService variants, PostMetrics metrics,
            TransactionTemplate transactions, Clock clock) {
        return new InstagramPublisher(socialPostRepository, house, connections, connectionHealth, instagram,
                instagramProperties, properties, captions, labels, variants, metrics, transactions, clock);
    }

    @Bean
    @Qualifier(HOUSE)
    InstagramPublisher houseInstagramPublisher(@Qualifier(HOUSE) SocialPostRepository housePostRepository,
            HouseProperties house, SocialConnectionRepository connections, ConnectionHealth connectionHealth,
            InstagramGraphClient instagram, InstagramProperties instagramProperties,
            InstagramPostingProperties properties, CaptionBuilder captions, ReferentialLabels labels,
            PhotoVariantService variants, PostMetrics metrics, TransactionTemplate transactions, Clock clock) {
        return new InstagramPublisher(housePostRepository, house, connections, connectionHealth, instagram,
                instagramProperties, properties, captions, labels, variants, metrics, transactions, clock);
    }
}
