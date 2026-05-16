package com.unihub.backend.service.csv;

import java.io.IOException;
import java.io.InputStream;

public interface CsvFetchStrategy {
    String getStrategyName();

    InputStream openStream() throws IOException;
}
