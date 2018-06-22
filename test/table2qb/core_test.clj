(ns table2qb.core-test
  (:require [clojure.test :refer :all]
            [table2qb.core :refer :all]
            [clojure.java.io :as io]
            [clojure.data :refer [diff]]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :refer [sail-repo ->connection query]]
            [grafter.extra.repository :refer [with-repository]]
            [grafter.extra.validation.pmd :as pmd]
            [grafter.extra.validation.pmd.dataset :as pmdd])
  (:import [java.io StringWriter]))

;; Test Helpers

(defn example [type name filename]
  (str "./examples/" name "/" type "/" filename))

(def example-csv (partial example "csv"))
(def example-csvw (partial example "csvw"))
(def example-ttl (partial example "ttl"))
  
(defn maps-match? [a b]
  (let [[a-only b-only _] (diff a b)]
    (is (nil? a-only) "Found only in first argument: ")
    (is (nil? b-only) "Found only in second argument: ")))

(defn first-by [attr val coll]
  "Finds first item in collection with attribute having value"
  (first (filter #(= val (attr %)) coll)))

(defmacro is-metadata-compatible [input-csv schema]
  `(testing "column sequence matches"
     (with-open [rdr# (io/reader ~input-csv)]
       (let [csv-columns# (-> rdr# (read-csv title->name) first keys ((partial map unkeyword)))
             meta-columns# (->> (get-in ~schema ["tableSchema" "columns"])
                                (remove #(get % "virtual" false))
                                (map #(get % "name")))]
         (is (= csv-columns# meta-columns#))))))

;; Conventions

(deftest identify-columns-test
  (let [conventions {:my-dim {:component_attachment "qb:dimension"}
                     :my-att {:component_attachment "qb:attribute"}}
        dimension? (identify-columns conventions "qb:dimension")
        attribute? (identify-columns conventions "qb:attribute")]
    (is (dimension? :my-dim))
    (is (not (dimension? :my-att)))
    (is (not (dimension? :unknown)))
    (is (attribute? :my-att))
    (is (not (attribute? :my-dim)))
    (is (not (attribute? :unknown)))))

;; Reference Data

(deftest components-test
  (testing "csv table"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "components.csv"))]
      (let [components (doall (components input-reader))]
        (testing "one row per component"
          (is (= 4 (count components))))
        (testing "one column per attribute"
          (testing "flow"
            (let [flow (first-by :label "Flow" components)]
              (are [attribute value] (= value (attribute flow))
                :notation "flow"
                :description "Direction in which trade is measured"
                :component_type "qb:DimensionProperty"
                :component_type_slug "dimension"
                :codelist (str domain-def "concept-scheme/flow-directions")
                :property_slug "flow"
                :class_slug "Flow"
                :parent_property nil)))
          (testing "gbp total"
            (let [gbp-total (first-by :label "GBP Total" components)]
              (are [attribute value] (= value (attribute gbp-total))
                :notation "gbp-total"
                :component_type "qb:MeasureProperty"
                :component_type_slug "measure"
                :property_slug "gbpTotal"
                :class_slug "GbpTotal"
                :parent_property "http://purl.org/linked-data/sdmx/2009/measure#obsValue")))))))
  (testing "json metadata"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "components.json"))]
      (maps-match? (read-json target-reader)
                   (components-metadata "components.csv")))))


(deftest codelists-test
  (testing "default case"
    (testing "csv table"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "flow-directions.csv"))]
        (let [codes (doall (codes input-reader))]
          (testing "one row per code"
            (is (= 2 (count codes))))
          (testing "one column per attribute"
            (is (= [:label :notation :parent_notation :sort_priority :top_concept_of :has_top_concept]
                   (-> codes first keys)))))))
    (testing "json metadata"
      (with-open [target-reader (io/reader (example-csvw "regional-trade" "flow-directions.json"))]
        (maps-match? (read-json target-reader)
                     (codelist-metadata
                      "flow-directions-codelist.csv"
                      "Flow Directions Codelist"
                      "flow-directions")))))
  (testing "with sort priority"
    (testing "csv table"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "sitc-sections.csv"))]
        (let [codes (doall (codes input-reader))]
          (testing "extra column for sort-priority"
            (is (= "0" (-> codes first :sort_priority)))
            (is (= [:label :notation :parent_notation :sort_priority :top_concept_of :has_top_concept]
                   (-> codes first keys)))))))
    (testing "json metadata"
      (with-open [target-reader (io/reader (example-csvw "regional-trade" "sitc-sections.json"))]
        (maps-match? (read-json target-reader)
                     (codelist-metadata
                      "sitc-sections-codelist.csv"
                      "SITC Sections Codelist"
                      "sitc-sections"))))))


;; Data

(deftest component-specifications-test
  (testing "returns a dataset of component-specifications"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))]
      (let [component-specifications (doall (component-specifications input-reader))]
        (testing "one row per component"
          (is (= 8 (count component-specifications))))
        (testing "geography component"
          (let [{:keys [:component_attachment :component_property]}
                (first-by :component_slug "geography" component-specifications)]
            (is (= component_attachment "qb:dimension"))
            (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
        (testing "compare with component-specifications.csv"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.csv"))]
              (is (= (set (read-csv target-reader))
                     (set component-specifications)))))
          (testing "serialised contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.csv"))]
              (let [string-writer (StringWriter.)]
                (write-csv string-writer (sort-by :component_slug component-specifications))
                (is (= (slurp target-reader)
                       (str string-writer)))))))
        (testing "compare with component-specifications.json"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.json"))]
              (maps-match? (read-json target-reader)
                           (component-specification-metadata
                            "regional-trade.slugged.normalised.csv"
                            "Regional Trade Component Specifications"
                            "regional-trade")))))))))

(deftest dataset-test
  (testing "compare with dataset.json"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "dataset.json"))]
      (maps-match? (read-json target-reader)
                      (dataset-metadata
                       "regional-trade.slugged.normalised.csv"
                       "Regional Trade"
                       "regional-trade")))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "data-structure-definition.json"))]
      (maps-match? (read-json target-reader)
                      (data-structure-definition-metadata
                       "regional-trade.slugged.normalised.csv"
                       "Regional Trade"
                       "regional-trade")))))

(deftest transform-colums-test
  (testing "converts columns with transforms specified"
    (is (maps-match? (transform-columns {:unit "£ million" :sitc_section "0 Food and Live Animals"})
                     {:unit "gbp-million" :sitc_section "0-food-and-live-animals"})))
  (testing "leaves columsn with no transform as is"
    (is (maps-match? (transform-columns {:label "not a slug" :curie "foo:bar"})
                     {:label "not a slug" :curie "foo:bar"}))))


(deftest observations-test
  (testing "sequence of observations"
    (testing "regional trade example"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))]
        (let [observations (doall (observations input-reader))]
          (testing "one observation per row"
            (is (= 44 (count observations))))
          (let [observation (first observations)]
            (testing "one column per component"
              (is (= 7 (count observation))))
            (testing "slugged columns"
              (are [expected actual] (= expected actual)
                "gbp-total" (:measure_type observation)
                "gbp-million" (:unit observation)
                "0-food-and-live-animals" (:sitc_section observation)
                "export" (:flow observation)))))))
    (testing "overseas trade example"
      (with-open [input-reader (io/reader (example-csv "overseas-trade" "ots-cn-sample.csv"))]
        (let [observations (doall (observations input-reader))]
          (testing "one observation per row"
            (is (= 20 (count observations))))
          (let [observation (first observations)]
            (testing "one column per component"
              (is (= 7 (count observation))))
            (testing "slugged columns"
              (are [expected actual] (= expected actual)
                "gbp-total" (:measure_type observation)
                "gbp-million" (:unit observation)
                "cn#cn8_28399000" (:combined_nomenclature observation)
                "export" (:flow observation))))))))
  (testing "observation metadata"
    (testing "regional trade example"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))
                  target-reader (io/reader (example-csvw "regional-trade" "observations.json"))]
        (maps-match? (read-json target-reader)
                     (observations-metadata input-reader
                                            "observations.csv"
                                            "regional-trade"))))
    (testing "overseas trade example"
      (with-open [input-reader (io/reader (example-csv "overseas-trade" "ots-cn-sample.csv"))]
        (let [obs-meta (observations-metadata input-reader "ignore-me.csv" "overseas-trade")]
          (is-metadata-compatible (example-csv "overseas-trade" "ots-cn-sample.csv")
                                  obs-meta))))))

(deftest used-codes-test
  (testing "codelists metadata"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "used-codes-codelists.json"))]
      (maps-match? (read-json target-reader)
                   (used-codes-codelists-metadata "regional-trade.slugged.normalised.csv"
                                                  "regional-trade"))))
  (testing "codes metadata"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))
                target-reader (io/reader (example-csvw "regional-trade" "used-codes-codes.json"))]
      (maps-match? (read-json target-reader)
                   (used-codes-codes-metadata input-reader
                                              "regional-trade.slugged.csv"
                                              "regional-trade")))))


(deftest validations-test
  (testing "all column must be recognised"
    (with-open [input-reader (io/reader (example-csv "validation" "unknown-columns.csv"))]
      (is (thrown-with-msg?
           Throwable #"Unrecognised column: Unknown"
           (observations input-reader)))))

  (testing "a measure should be present"
    (testing "under the measures-dimension approach"
      (testing "with a single measure-type column"
        (with-open [input-reader (io/reader (example-csv "validation" "measure-type-single.csv"))]
          (is (seq? (observations input-reader))))
        (testing "and a measure column"
          ;; TODO - should fail (i.e. either type or measure provided)
          ))
      (testing "with multiple measure-type columns"
        ;; TODO - should fail - can only have one measure-type dimension
        ;; not sure this is worth testing until it's a problem!
        ) 
      (testing "with no measure-type columns"
        (with-open [input-reader (io/reader (example-csv "validation" "measure-type-missing.csv"))]
          (is (thrown-with-msg?
               Throwable #"No measure type column"
               (component-specifications input-reader))))))
    (testing "under the multi-measures approach"
      ;; TODO - this isn't implemented yet
      ;; Should require that no measure-type component be provided if there is a measure column
      ))

  (testing "values must be provided for all dimensions"
    (with-open [input-reader (io/reader (example-csv "validation" "dimension-values-missing.csv"))]
      (is (thrown? Throwable
                   (doall (observations input-reader)))))))

(defn load-from-file [conn file]
  (rdf/add conn (rdf/statements (io/file file))))

(deftest integration-test
  (testing "Validates table2qb outputs against pmd, dataset, and cube tests"
    (testing "Overseas Trade"
      (with-repository [repo (sail-repo)]
        (with-open [conn (->connection repo)]
          ;; Third party vocabularies
          (doseq [file ["./examples/vocabularies/sdmx-dimension.ttl"
                        "./examples/vocabularies/qb.ttl"
                        "./examples/overseas-trade/vocabularies/2012.rdf"
                        "./examples/overseas-trade/vocabularies/CN_2015_20180206_105537.ttl"]]
            (load-from-file conn file))

          (let [stmts (concat
                       ;; Existing reference data
                       (codelist-pipeline "./examples/regional-trade/csv/flow-directions.csv" "Flow Directions" "flow-directions")
                       (codelist-pipeline "./examples/regional-trade/csv/sitc-sections.csv" "Flow Directions" "sitc-sections")
                       (codelist-pipeline "./examples/regional-trade/csv/units.csv" "Measurement Units" "measurement-units")
                       (components-pipeline "./examples/regional-trade/csv/components.csv")

                       ;; This dataset
                       (codelist-pipeline "./examples/overseas-trade/csv/countries.csv" "Countries" "countries")
                       (components-pipeline "./examples/overseas-trade/csv/components.csv")
                       (cube-pipeline "./examples/overseas-trade/csv/ots-cn-sample.csv" "Overseas Trade Sample" "overseas-trade-sample"))]
            (rdf/add conn stmts)))
        (testing "PMD Validation"
          (is (empty? (pmd/errors repo))))
        (testing "PMD Dataset Validation"
          (is (empty? (remove #{"is missing a reference area dimension"
                                "is not a pmd:Dataset"
                                "is missing a pmd:graph"}
                              (pmdd/errors repo (str domain-data "overseas-trade-sample"))))))
        (testing "Sort Priority"
          (with-open [conn (->connection repo)]
            (let [results (query conn (slurp "./examples/validation/sparql/sort-priority.sparql"))
                  schemes (->> results (map (comp str :scheme)) distinct)]
              (testing "may be provided"
                (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
              (testing "is optional"
                (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))))))


;; TODO: Need to label components and their used-code codelists
;; TODO: Vocabulary for pmd:usedCode

