package dev.starq.picassolve.entity;

import dev.starq.picassolve.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
	@Mapping(target = "sid", ignore = true)
	UserDto toDto(User user);
}
