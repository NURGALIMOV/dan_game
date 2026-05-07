package com.dangame.detective.mapper;

import com.dangame.detective.dto.Dtos.AchievementDto;
import com.dangame.detective.entity.AchievementEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AchievementMapper {

    AchievementDto toDto(AchievementEntity entity);
}
