package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.dto.Request.UserUpdateRequest;
import dev.starq.picassolve.dto.UserDto;
import java.util.List;
import java.util.UUID;

public interface UserService {
	UserDto create(UserCreateRequest request);

	UserDto update(UUID id, UserUpdateRequest request);

	void changePassword(String username, String currentPassword, String newPassword);

	List<UserDto> findAll();

	UserDto findByName(String name);

	void delete(UUID id);

	UserDto updateProfile(String currentName, String newName, Integer newTeam, String password);

	void deleteUser(String username, String password);
}
