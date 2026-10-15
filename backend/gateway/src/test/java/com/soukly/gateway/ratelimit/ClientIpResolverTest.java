package com.soukly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedForWhenNoProxyIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertThat(resolver.resolve(request("198.51.100.7", "203.0.113.1"))).isEqualTo("198.51.100.7");
    }

    @Test
    void readsTheLastForwardedForEntryFromATrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("172.16.0.0/12"));

        assertThat(resolver.resolve(request("172.18.0.3", "10.9.9.9, 203.0.113.1"))).isEqualTo("203.0.113.1");
    }

    @Test
    void ignoresForwardedForFromAnUntrustedAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("172.16.0.0/12"));

        assertThat(resolver.resolve(request("192.0.2.10", "203.0.113.1"))).isEqualTo("192.0.2.10");
    }

    @Test
    void usesTheConnectionWhenATrustedProxySendsNoForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("127.0.0.1"));

        assertThat(resolver.resolve(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
    }

    @Test
    void ipRangesSupportIpv4Ipv6AndSingleAddresses() {
        assertThat(IpRange.parse("10.0.0.0/8").contains("10.200.1.1")).isTrue();
        assertThat(IpRange.parse("10.0.0.0/8").contains("11.0.0.1")).isFalse();
        assertThat(IpRange.parse("172.16.0.0/12").contains("172.31.255.255")).isTrue();
        assertThat(IpRange.parse("172.16.0.0/12").contains("172.32.0.1")).isFalse();
        assertThat(IpRange.parse("127.0.0.1").contains("127.0.0.1")).isTrue();
        assertThat(IpRange.parse("fd00::/8").contains("fd12::1")).isTrue();
        assertThat(IpRange.parse("fd00::/8").contains("10.0.0.1")).isFalse();
        assertThat(IpRange.parse("10.0.0.0/8").contains("not-an-ip")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> IpRange.parse("caddy"));
        assertThatIllegalArgumentException().isThrownBy(() -> IpRange.parse("10.0.0.0/33"));
    }

    private static MockServerHttpRequest request(String remoteIp, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("/api/users/login")
                .remoteAddress(new InetSocketAddress(remoteIp, 50000));
        if (forwardedFor != null) {
            builder.header(ClientIpResolver.X_FORWARDED_FOR, forwardedFor);
        }
        return builder.build();
    }
}
