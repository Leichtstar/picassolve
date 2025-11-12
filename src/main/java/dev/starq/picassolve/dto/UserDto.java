package dev.starq.picassolve.dto;

import dev.starq.picassolve.entity.User;
import java.util.UUID;

public record UserDto(
	UUID id,
	String name,
	int team,
	int score,
	User.Role role
)
{}
