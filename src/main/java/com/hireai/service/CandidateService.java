package com.hireai.service;

import com.hireai.domain.dto.request.CandidateUpdateRequest;
import com.hireai.domain.dto.response.CandidateResponse;
import com.hireai.domain.entity.Candidate;
import com.hireai.domain.entity.User;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;

    @Transactional(readOnly = true)
    public CandidateResponse getProfile(User user) {
        Candidate candidate = findByUserOrThrow(user);
        return toResponse(candidate);
    }

    @Transactional
    public CandidateResponse updateProfile(CandidateUpdateRequest request, User user) {
        Candidate candidate = findByUserOrThrow(user);

        if (request.getPhone() != null) candidate.setPhone(request.getPhone());
        if (request.getLinkedinUrl() != null) candidate.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getYearsOfExperience() != null) candidate.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getCurrentTitle() != null) candidate.setCurrentTitle(request.getCurrentTitle());

        return toResponse(candidateRepository.save(candidate));
    }

    private Candidate findByUserOrThrow(User user) {
        return candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for user: " + user.getId()));
    }

    private CandidateResponse toResponse(Candidate candidate) {
        return CandidateResponse.builder()
                .id(candidate.getId())
                .userId(candidate.getUser().getId())
                .fullName(candidate.getUser().getFullName())
                .email(candidate.getUser().getEmail())
                .phone(candidate.getPhone())
                .linkedinUrl(candidate.getLinkedinUrl())
                .yearsOfExperience(candidate.getYearsOfExperience())
                .currentTitle(candidate.getCurrentTitle())
                .createdAt(candidate.getCreatedAt())
                .updatedAt(candidate.getUpdatedAt())
                .build();
    }
}
