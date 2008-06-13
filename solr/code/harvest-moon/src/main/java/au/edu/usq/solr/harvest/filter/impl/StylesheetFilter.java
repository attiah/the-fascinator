/* 
 * Sun of Fedora - Solr Portal
 * Copyright (C) 2008  University of Southern Queensland
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package au.edu.usq.solr.harvest.filter.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import au.edu.usq.solr.harvest.filter.BaseFilter;
import au.edu.usq.solr.harvest.filter.FilterException;

public class StylesheetFilter extends BaseFilter {

    private Transformer transformer;

    public StylesheetFilter(InputStream xsl) throws FilterException {
        this(xsl, null);
    }

    public StylesheetFilter(InputStream xsl, Map<String, String> params)
        throws FilterException {
        super("Stylesheet", true);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Templates t = tf.newTemplates(new StreamSource(xsl));
            transformer = t.newTransformer();
            if (params != null) {
                for (String key : params.keySet()) {
                    transformer.setParameter(key, params.get(key));
                }
            }
        } catch (TransformerConfigurationException tce) {
            throw new FilterException("Failed to load stylesheet", tce);
        }
    }

    @Override
    public void filter(String id, InputStream in, OutputStream out)
        throws FilterException {
        Source source = new StreamSource(in);
        Result result = new StreamResult(out);
        try {
            transformer.transform(source, result);
        } catch (TransformerException te) {
            throw new FilterException("Failed to transform", te);
        }
    }
}
