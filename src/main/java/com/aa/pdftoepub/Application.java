package com.aa.pdftoepub;

import org.apache.pdfbox.Loader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Application {
    public static void main(String[] args) throws Exception {
        var file = "/Users/aalonzi/Downloads/Physics of Light and Optics.pdf";
        try (var pdf = Loader.loadPDF(new File(file))) {
            var output = new StringBuilder();

            var sb = new PageConverter(pdf.getPage(0)).convert();
            output.append(sb);

//            pdf.getPages().forEach(page -> {
//                var sb = new PageConverter(page).convert();
//                output.append(sb);
//            });

            var html = "<html>\n" +
                    "  <head>\n" +
                    "  </head>\n" +
                    "  <body>\n" +
                    output +
                    "  </body>\n" +
                    "</html>";

            var path = Paths.get(file.replace(".pdf", ".html"));
            Files.write(path, html.getBytes());
        }
    }
}
