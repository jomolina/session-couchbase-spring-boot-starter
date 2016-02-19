package com.github.mkopylec.sessioncouchbase.persistent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.session.SessionRepository;

import java.util.HashMap;
import java.util.Map;

import static com.couchbase.client.java.document.json.JsonObject.from;
import static java.lang.System.currentTimeMillis;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notNull;

public class CouchbaseSessionRepository implements SessionRepository<CouchbaseSession> {

    protected static final String GLOBAL_NAMESPACE = "global";

    private static final Logger log = getLogger(CouchbaseSessionRepository.class);

    protected final CouchbaseDao dao;
    protected final ObjectMapper mapper;
    protected final String namespace;
    protected final int sessionTimeout;
    protected final Serializer serializer;

    public CouchbaseSessionRepository(CouchbaseDao dao, String namespace, ObjectMapper mapper, int sessionTimeout, Serializer serializer) {
        notNull(dao, "Missing couchbase data access object");
        notNull(mapper, "Missing JSON object mapper");
        hasText(namespace, "Empty HTTP session namespace");
        isTrue(!namespace.equals(GLOBAL_NAMESPACE), "Forbidden HTTP session namespace '" + namespace + "'");
        notNull(serializer, "Missing object serializer");
        this.dao = dao;
        this.mapper = mapper;
        this.namespace = namespace.trim();
        this.sessionTimeout = sessionTimeout;
        this.serializer = serializer;
    }

    @Override
    public CouchbaseSession createSession() {
        log.debug("Creating HTTP session");

        CouchbaseSession session = new CouchbaseSession(sessionTimeout);
        Map<String, Map<String, Object>> sessionData = new HashMap<>(2);
        sessionData.put(GLOBAL_NAMESPACE, session.getGlobalAttributes());
        sessionData.put(namespace, session.getNamespaceAttributes());
        SessionEntity sessionEntity = new SessionEntity(session.getId(), sessionData);
        dao.save(sessionEntity);

        return session;
    }

    @Override
    public void save(CouchbaseSession session) {
        log.debug("Saving HTTP session with ID {}", session.getId());

        Map<String, Object> serializedGlobal = serializer.serializeSessionAttributes(session.getGlobalAttributes());
        Map<String, Object> serializedNamespace = serializer.serializeSessionAttributes(session.getNamespaceAttributes());
        dao.updateSession(from(serializedGlobal), GLOBAL_NAMESPACE, session.getId());
        dao.updateSession(from(serializedNamespace), namespace, session.getId());
    }

    @Override
    public CouchbaseSession getSession(String id) {
        log.debug("Getting HTTP session with ID {}", id);

        Map<String, Object> globalAttributes = dao.findSessionAttributes(id, GLOBAL_NAMESPACE);
        Map<String, Object> namespaceAttributes = dao.findSessionAttributes(id, namespace);

        if (globalAttributes == null && namespaceAttributes == null) {
            log.debug("HTTP session with ID {} not found", id);
            return null;
        }

        notNull(globalAttributes, "Invalid state of HTTP session persisted in couchbase. Missing global attributes.");

        Map<String, Object> deserializedGlobal = serializer.deserializeSessionAttributes(globalAttributes);
        Map<String, Object> deserializedNamespace = serializer.deserializeSessionAttributes(namespaceAttributes);
        CouchbaseSession session = new CouchbaseSession(id, deserializedGlobal, deserializedNamespace);
        if (session.isExpired()) {
            log.debug("HTTP session with ID {} has expired", id);
            delete(id);
            return null;
        }
        session.setLastAccessedTime(currentTimeMillis());

        return session;
    }

    @Override
    public void delete(String id) {
        log.debug("Deleting HTTP session with ID {}", id);
        dao.delete(id);
    }
}
