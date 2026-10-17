package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.ImageTypeService;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ScanTypeRoutingTest {
    @Test void bothLegacyTypeEndpointAndGenericUpdateUseAuditedMutation() {
        ScanMapper mapper=mock(ScanMapper.class);ImageTypeService types=mock(ImageTypeService.class);
        ScanServiceImpl service=new ScanServiceImpl(mapper,types);
        service.updateImageType(1,15);verify(types).manual(1,15);verify(mapper,never()).updateImageType(anyInt(),anyInt());
        Scan scan=new Scan();scan.setId(2);scan.setBtype(11);service.update(scan);verify(types).manual(2,11);
    }
}
