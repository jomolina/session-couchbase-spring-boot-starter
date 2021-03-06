package com.github.mkopylec.sessioncouchbase.persistent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mkopylec.sessioncouchbase.SessionCouchbaseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionStrategy;
import org.springframework.session.web.http.MultiHttpSessionStrategy;

import java.util.List;

@Configuration("sessionPersistentConfiguration")
@EnableCouchbaseRepositories
@EnableConfigurationProperties(SessionCouchbaseProperties.class)
@ConditionalOnProperty(name = "session-couchbase.in-memory.enabled", havingValue = "false", matchIfMissing = true)
public class PersistentConfiguration extends AbstractCouchbaseConfiguration {

    @Autowired
    private SessionCouchbaseProperties sessionCouchbase;

    @Bean
    @ConditionalOnMissingBean
    public CouchbaseDao couchbaseDao(CouchbaseTemplate couchbase) {
        return new CouchbaseDao(couchbase);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiHttpSessionStrategy multiHttpSessionStrategy(CouchbaseDao dao, Serializer serializer) {
        return new DelegatingSessionStrategy(new CookieHttpSessionStrategy(), dao, sessionCouchbase.getPersistent().getNamespace(), serializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        return new Serializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRepository sessionRepository(CouchbaseDao dao, ObjectMapper mapper, Serializer serializer) {
        return new CouchbaseSessionRepository(
                dao, sessionCouchbase.getPersistent().getNamespace(), mapper, sessionCouchbase.getTimeoutInSeconds(), serializer, sessionCouchbase.getPersistent().getPrincipalSessions().isEnabled()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Override
    protected List<String> getBootstrapHosts() {
        return sessionCouchbase.getPersistent().getCouchbase().getHosts();
    }

    @Override
    protected String getBucketName() {
        return sessionCouchbase.getPersistent().getCouchbase().getBucketName();
    }

    @Override
    protected String getBucketPassword() {
        return sessionCouchbase.getPersistent().getCouchbase().getPassword();
    }
}
