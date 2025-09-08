package dev.starq.picassolve.repository;

import dev.starq.picassolve.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordRepository extends JpaRepository<Word, Long> { }
