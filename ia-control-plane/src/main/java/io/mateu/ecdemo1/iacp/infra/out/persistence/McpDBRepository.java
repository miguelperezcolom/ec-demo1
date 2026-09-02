package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.repository.McpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.Mcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class McpDBRepository implements McpRepository {

    final McpEntityRepository entities;

    @Override
    public Mcp save(Mcp mcp) {
        var e = entities.findById(mcp.getId().value()).orElseGet(McpEntity::new);
        e.setId(mcp.getId().value());
        e.setName(mcp.getName().value());
        e.setUrl(mcp.getEndpoint().value());
        e.setTransport(mcp.getTransport().name());
        e.setTimeoutSeconds(mcp.getTimeout().toSeconds());
        e.setDescription(mcp.getDescription());
        e.setEnabled(mcp.getEnabled().value());
        e.setCreated(mcp.getCreated().value());
        entities.save(e);
        return mcp;
    }

    @Override
    public Optional<Mcp> findById(McpId id) {
        return entities.findById(id.value()).map(McpDBRepository::toDomain);
    }

    @Override
    public List<Mcp> findAll() {
        return entities.findAll().stream().map(McpDBRepository::toDomain).toList();
    }

    @Override
    public void deleteAllById(List<McpId> ids) {
        entities.deleteAllById(ids.stream().map(McpId::value).toList());
    }

    @Override
    public boolean existsById(McpId id) {
        return entities.existsById(id.value());
    }

    static Mcp toDomain(McpEntity e) {
        return new Mcp(
                new McpId(e.getId()),
                new Name(e.getName()),
                new Endpoint(e.getUrl()),
                McpTransport.valueOf(e.getTransport()),
                Duration.ofSeconds(e.getTimeoutSeconds()),
                e.getDescription(),
                new Enabled(e.isEnabled()),
                new Time(e.getCreated()));
    }
}
