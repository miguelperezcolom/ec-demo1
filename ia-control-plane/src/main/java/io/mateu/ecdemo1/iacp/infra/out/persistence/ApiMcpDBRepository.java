package io.mateu.ecdemo1.iacp.infra.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.ApiMcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiCredential;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ExposedTool;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMcpDBRepository implements ApiMcpRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    final ApiMcpEntityRepository entities;

    @Override
    public ApiMcp save(ApiMcp api) {
        var e = entities.findById(api.getId().value()).orElseGet(ApiMcpEntity::new);
        e.setId(api.getId().value());
        e.setName(api.getName().value());
        e.setKind(api.getKind().name());
        e.setBaseUrl(api.getBaseUrl().value());
        e.setSpecUrl(api.getSpecUrl().value());
        e.setCredential(api.getCredential().cipherText());
        e.setToolsJson(writeTools(api.getTools()));
        e.setDescription(api.getDescription());
        e.setEnabled(api.getEnabled().value());
        e.setCreated(api.getCreated().value());
        entities.save(e);
        return api;
    }

    @Override
    public Optional<ApiMcp> findById(ApiMcpId id) {
        return entities.findById(id.value()).map(ApiMcpDBRepository::toDomain);
    }

    @Override
    public List<ApiMcp> findAll() {
        return entities.findAll().stream().map(ApiMcpDBRepository::toDomain).toList();
    }

    @Override
    public void deleteAllById(List<ApiMcpId> ids) {
        entities.deleteAllById(ids.stream().map(ApiMcpId::value).toList());
    }

    @Override
    public boolean existsById(ApiMcpId id) {
        return entities.existsById(id.value());
    }

    static ApiMcp toDomain(ApiMcpEntity e) {
        return new ApiMcp(
                new ApiMcpId(e.getId()),
                new Name(e.getName()),
                ApiKind.valueOf(e.getKind()),
                new Endpoint(e.getBaseUrl()),
                new Endpoint(e.getSpecUrl()),
                new ApiCredential(e.getCredential()),
                readTools(e.getToolsJson(), e.getId()),
                e.getDescription(),
                new Enabled(e.isEnabled()),
                new Time(e.getCreated()));
    }

    private static String writeTools(List<ExposedTool> tools) {
        try {
            return JSON.writeValueAsString(tools == null ? List.of() : tools);
        } catch (Exception ex) {
            // Refused rather than stored empty: silently saving an entry with no tools would turn
            // a serialisation bug into "this API offers nothing", which nobody would investigate.
            throw new IllegalStateException("Could not store the exposed tools", ex);
        }
    }

    /**
     * Unreadable JSON leaves the entry with NO tools rather than failing the read.
     *
     * <p>The asymmetry with the write above is deliberate. Failing to save loses one edit and says
     * so; failing to load takes the whole catalogue screen down over one bad row, and the row is
     * still there to fix. An entry with no tools is visibly not usable — ApiMcp.isUsable says so —
     * so this degrades to something an operator can see rather than to something that pretends.
     */
    private static List<ExposedTool> readTools(String json, String id) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<ExposedTool>>() {});
        } catch (Exception ex) {
            log.error("The exposed tools of API MCP server {} could not be read — it will show as "
                    + "offering none until they are set again", id, ex);
            return List.of();
        }
    }
}
