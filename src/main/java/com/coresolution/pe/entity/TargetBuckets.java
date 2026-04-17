package com.coresolution.pe.entity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
public class TargetBuckets {
    private final List<UserPE> ghTeam;
    private final List<UserPE> medicalDepts;
    private final List<UserPE> subHeads;
    private final List<UserPE> subMembers;

    public Set<String> allTargetIds() {
        return Stream.of(ghTeam, medicalDepts, subHeads, subMembers)
                .filter(list -> list != null)
                .flatMap(List::stream)
                .map(UserPE::getId)
                .collect(Collectors.toSet());
    }

}
