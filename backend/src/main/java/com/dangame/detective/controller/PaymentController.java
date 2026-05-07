package com.dangame.detective.controller;

import com.dangame.detective.ApiException;
import com.dangame.detective.dto.Dtos.Payment;
import com.dangame.detective.entity.ProgressEntity;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.gamedata.Cases;
import com.dangame.detective.gamedata.Cases.Case;
import com.dangame.detective.repo.ProgressRepository;
import com.dangame.detective.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final ProgressRepository progresses;

    @PostMapping("/payment/init")
    public Map<String, Object> init(
        @CurrentUser UserEntity user,
        @Valid @RequestBody Payment req
    ) {
        Case cs = Cases.CASES.get(req.caseId());
        if (cs == null) throw new ApiException(HttpStatus.NOT_FOUND, "Дело не найдено");

        var resp = new LinkedHashMap<String, Object>();
        resp.put("payment_url", "/payment_success?case_id=" + req.caseId());
        resp.put("amount",      cs.price());
        resp.put("description", "Полная версия: " + cs.title());
        return resp;
    }

    @PostMapping("/payment/confirm")
    @Transactional
    public Map<String, Object> confirm(
        @CurrentUser UserEntity user,
        @Valid @RequestBody Payment req
    ) {
        progresses.findByUserIdAndCaseId(user.getId(), req.caseId()).ifPresentOrElse(
            p -> p.setPaid(1),
            () -> {
                ProgressEntity fresh = new ProgressEntity();
                fresh.setUserId(user.getId());
                fresh.setCaseId(req.caseId());
                fresh.setPaid(1);
                progresses.save(fresh);
            }
        );
        return Map.of("success", true, "message", "Доступ открыт");
    }
}
