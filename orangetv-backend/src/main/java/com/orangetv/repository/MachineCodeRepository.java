package com.orangetv.repository;

import com.orangetv.entity.MachineCode;
import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineCodeRepository extends JpaRepository<MachineCode, Long> {

    List<MachineCode> findByUser(User user);

    List<MachineCode> findByUserId(Long userId);

    Optional<MachineCode> findByMachineCode(String machineCode);

    Optional<MachineCode> findByUserAndMachineCode(User user, String machineCode);

    Optional<MachineCode> findByUserIdAndMachineCode(Long userId, String machineCode);

    boolean existsByUserAndMachineCode(User user, String machineCode);

    int countByUser(User user);

    int countByUserId(Long userId);

    void deleteByUserAndMachineCode(User user, String machineCode);
}
