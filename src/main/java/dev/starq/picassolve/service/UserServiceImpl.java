package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.dto.Request.UserUpdateRequest;
import dev.starq.picassolve.dto.UserDto;
import dev.starq.picassolve.entity.User;
import dev.starq.picassolve.entity.UserMapper;
import dev.starq.picassolve.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	@Override
	public UserDto create(UserCreateRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		String name = normalize(request.name());
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (userRepository.existsByName(name)) {
			throw new IllegalArgumentException("name already exists: " + name);
		}

		String rawPassword = normalize(request.password());
		if (rawPassword.isBlank()) {
			throw new IllegalArgumentException("password must not be blank");
		}
		int team = request.team();
		if (team < 0) {
			throw new IllegalArgumentException("team must be zero or positive");
		}

		User createdUser = User.builder()
				.name(name)
				.password(passwordEncoder.encode(rawPassword))
				.team(team)
				.build();

		return userMapper.toDto(userRepository.save(createdUser));
	}

	@Override
	public UserDto update(UUID id, UserUpdateRequest request) {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(request, "request must not be null");

		User user = userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("user not found: " + id));

		String newName = normalize(request.name());
		if (!newName.isBlank() && !newName.equals(user.getName())) {
			if (userRepository.existsByName(newName)) {
				throw new IllegalArgumentException("name already exists: " + newName);
			}
			user.setName(newName);
		}
		String newPassword = request.password();
		if (newPassword != null && !newPassword.isBlank()) {
			user.setPassword(passwordEncoder.encode(newPassword.trim()));
		}

		return userMapper.toDto(userRepository.save(user));
	}

	@Override
	public void changePassword(String username, String currentPassword, String newPassword) {
		String normalizedName = normalize(username);
		if (normalizedName.isBlank()) {
			throw new IllegalArgumentException("유효하지 않은 사용자입니다.");
		}
		String normalizedCurrent = normalize(currentPassword);
		String normalizedNew = normalize(newPassword);
		if (normalizedCurrent.isBlank()) {
			throw new IllegalArgumentException("현재 비밀번호를 입력하세요.");
		}
		if (normalizedNew.isBlank()) {
			throw new IllegalArgumentException("새 비밀번호를 입력하세요.");
		}
		if (normalizedCurrent.equals(normalizedNew)) {
			throw new IllegalArgumentException("새 비밀번호가 기존 비밀번호와 동일합니다.");
		}

		User user = userRepository.findByName(normalizedName)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(normalizedCurrent, user.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
		}

		user.setPassword(passwordEncoder.encode(normalizedNew));
		userRepository.save(user);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserDto> findAll() {
		return userRepository.findAll().stream()
				.map(userMapper::toDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public UserDto findByName(String name) {
		return userRepository.findByName(normalize(name))
				.map(userMapper::toDto)
				.orElse(null);
	}

	@Override
	public void delete(UUID id) {
		if (id == null)
			return;
		if (userRepository.existsById(id)) {
			userRepository.deleteById(id);
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
