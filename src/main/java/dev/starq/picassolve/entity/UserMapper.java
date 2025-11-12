package dev.starq.picassolve.entity;

import dev.starq.picassolve.dto.UserDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
	UserDto toDto(User user);
}
