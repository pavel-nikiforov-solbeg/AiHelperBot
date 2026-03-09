package com.solbeg.sas.perfmgmnt.repository;

import com.solbeg.sas.perfmgmnt.model.BotFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BotFeedbackRepository extends JpaRepository<BotFeedback, Long> {
}
