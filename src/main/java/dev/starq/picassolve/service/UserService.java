package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.dto.Request.UserUpdateRequest;
import dev.starq.picassolve.dto.UserDto;
import java.util.List;
import java.util.UUID;

public interface UserService {
	UserDto create(UserCreateRequest request);
	UserDto update(UUID id, UserUpdateRequest request);
	List<UserDto> findAll();
	void delete(UUID id);
}
