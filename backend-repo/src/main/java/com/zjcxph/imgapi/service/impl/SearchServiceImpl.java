package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final SearchMapper searchMapper;

    public SearchServiceImpl(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Override
    public List<Patient> getBAHByID(String id) {
        return searchMapper.findBAHByIDCard(id);
    }

    @Override
    public List<IdCardArchiveSearchResponse.ArchiveCase> getArchiveCasesByID(String idCard) {
        List<IdCardArchiveSearchResponse.ArchiveCase> cases = new ArrayList<>();
        for (Patient patient : searchMapper.findBAHByIDCard(idCard)) {
            String bah = MedicalRecordCodeUtils.normalizeOrEmpty(patient.getBah());
            if (bah.isBlank()) {
                continue;
            }

            List<String> rawShelfNumbers = searchMapper.findSjhByBah(
                    bah,
                    MedicalRecordCodeUtils.toSearchTerm(bah)
            );
            LinkedHashSet<String> shelfNumbers = new LinkedHashSet<>();
            for (String shelfNumber : rawShelfNumbers) {
                String normalized = MedicalRecordCodeUtils.normalizeOrEmpty(shelfNumber);
                if (!normalized.isBlank()) {
                    shelfNumbers.add(normalized);
                }
            }

            if (shelfNumbers.isEmpty()) {
                cases.add(toArchiveCase(patient, bah, ""));
            } else {
                for (String sjh : shelfNumbers) {
                    cases.add(toArchiveCase(patient, bah, sjh));
                }
            }
        }
        return cases;
    }

    @Override
    public List<Patient> getPatientByBah(String bah) {
        return searchMapper.findPatientByBah(
                MedicalRecordCodeUtils.normalizeOrEmpty(bah),
                MedicalRecordCodeUtils.toSearchTerm(bah)
        );
    }

    private IdCardArchiveSearchResponse.ArchiveCase toArchiveCase(Patient patient, String bah, String sjh) {
        return new IdCardArchiveSearchResponse.ArchiveCase(
                patient.getId(),
                bah,
                sjh,
                patient.getName(),
                patient.getAdmissiontime(),
                patient.getDepartment()
        );
    }
}
