package com.dangame.detective.mapper;

import com.dangame.detective.dto.Dtos.AuthResponse;
import com.dangame.detective.dto.Dtos.LeaderEntryDto;
import com.dangame.detective.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    AuthResponse toAuthResponse(UserEntity user);

    @Mapping(source = "casesSolved", target = "casesSolved")
    LeaderEntryDto toLeaderEntry(UserEntity user);
}
