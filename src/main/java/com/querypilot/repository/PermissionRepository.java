package com.querypilot.repository;

import com.querypilot.model.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByUserIdAndConnectionId(Long userId, Long connectionId);

    List<Permission> findByUserId(Long userId);

    List<Permission> findByConnectionId(Long connectionId);

    Optional<Permission> findByUserIdAndConnectionId(Long userId, Long connectionId);

    void deleteByConnectionId(Long connectionId);
}
