
package org.folio.circulation.domain.representations.anonymization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Response schema for anonymize loans request
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder( {
    "anonymizedLoans",
    "errors"
})
public class AnonymizeStorageLoansResponse {

    /**
     * Successfully anonymized loan ids
     * 
     */
    @JsonProperty("anonymizedLoans")
    @JsonPropertyDescription("Successfully anonymized loan ids")
    private List<String> anonymizedLoans = new ArrayList<String>();
    @JsonProperty("errors")
    private Object errors;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Successfully anonymized loan ids
     * 
     */
    @JsonProperty("anonymizedLoans")
    public List<String> getAnonymizedLoans() {
        return anonymizedLoans;
    }

    /**
     * Successfully anonymized loan ids
     * 
     */
    @JsonProperty("anonymizedLoans")
    public void setAnonymizedLoans(List<String> anonymizedLoans) {
        this.anonymizedLoans = anonymizedLoans;
    }

    public AnonymizeStorageLoansResponse withAnonymizedLoans(List<String> anonymizedLoans) {
        this.anonymizedLoans = anonymizedLoans;
        return this;
    }

    @JsonProperty("errors")
    public Object getErrors() {
        return errors;
    }

    @JsonProperty("errors")
    public void setErrors(Object errors) {
        this.errors = errors;
    }

    public AnonymizeStorageLoansResponse withErrors(Object errors) {
        this.errors = errors;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public AnonymizeStorageLoansResponse withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
