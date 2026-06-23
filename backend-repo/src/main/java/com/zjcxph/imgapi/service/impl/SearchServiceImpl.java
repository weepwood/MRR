package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final SearchMapper searchMapper  ;

    @Autowired
    public SearchServiceImpl(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Override
    public List<Patient> getBAHByID(String id) {
        return searchMapper.findBAHByIDCard(id);
    }

    @Override
    public List<Patient> getPatientByBah(String bah) {
        return searchMapper.findPatientByBah(bah);
    }
}
