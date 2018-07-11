# table2cube

![animated tesseract](https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Tesseract-1K.gif/240px-Tesseract-1K.gif)

## Overview

_extend this_

This project transforms tables of observations and reference data into [rdf data cube](https://www.w3.org/TR/vocab-data-cube/) resources specified as [csvw](https://github.com/w3c/csvw).

## How to run table2qb

_UPDATE THIS ONCE WE'VE DONE ISSUES [47](https://github.com/Swirrl/table2qb/issues/47) and [45](https://github.com/Swirrl/table2qb/issues/45)_.

```BASE_URI=your_domain java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec pipeline --input-csv input_file --column-config config_file --output-file output_file```

### pipeline

This parameter must be one of: `cube-pipeline` | `components-pipeline` | `codelist-pipeline`


### input_file

_a csv file of the correct structure - contents must correspond to the choice of pipeline - see section below describing the structure required for each_.

_explain how the config file ('columns.csv') is used to determine how the data is interpreted_

### config_file

_explain the structure of the config file_

### output_file

The output of the process: a single file as RDF in Turtle format.


### Observation Data

_needs updated_

The observation input table should be arranged as [tidy-data](http://vita.had.co.nz/papers/tidy-data.pdf) e.g. one row per observation, one column per component (i.e. dimension, attribute or measure). The output is a set of csvw documents - i.e. csv with json-ld metadata - that can be translated into RDF via a [csv2rdf](http://www.w3.org/TR/csv2rdf/) processor. The outputs that make up the cube are:

- `observations.csv`: this goes through some transformations to standardise the cell values from arbitrary strings to slugs or other notations that are ready to be combined into URIs
- `component-specifications.csv`: this is a normalisation of the observations table that has one row per component
- `dataset.json`: the `qb:DataSet`
- `data-structure-definition.json`: the `qb:DataStructureDefinition`
- `component-specifications.json`: the set of `qb:ComponentSpecification`s (one per dimension, attribute and measure in the input)
- `observations.json`: the set of `qb:Observation`s (one per row in the input)

We also provide a set of `skos:ConceptScheme`s enumerating all of the codes used in each of the componentss (via `used-codes-scheme.json` and `used-codes-concepts.json`). These are useful for navigating within a cube by using the marginals - in other words this saves you from having to scan through all of the observations in order to establish the extent of the cube.

### Definition of components

_needs updated_

The project provides pipelines for preparing reference data. These can be used for managing reference data across multiple `qb:DataSet`s.

- Components: given a tidy-data input of one component per row, this pipeline creates a `components.csv` file and a `components.json` for creating `qb:ComponentProperty`s in an `owl:Ontology`. Note that components are the dimensions, attributes and measures themselves whereas the component-specifications are what links these to a given data-structure-definition.


### Definition of code-lists

_needs updated_ 

- Codelists: given a tidy-data input of one code per row, this pipeline creates a `codelist.csv` file and a `codelist.json` for creating `skos:Concepts` in an `skos:ConceptScheme`. Note that these codelists describe the universal set of codes that may be the object of a component (making it a `qb:CodedProperty`) not the (sub)set that have been used within a cube.

## Configuration

The table2qb pipeline is configured with a CSV file describing the columns it can expect to find in the input CSV files. The location of this file is specified with the `--column-config` parameter.

The CSV file should have the following columns:

_check this is still correct_

- `title` - a human readable title (like csvw:title) that will be provided in the (first) header row of the input
- `name` - a machine-readable identifier (like csvw:name) used in uri templates
- `component_attachment` - how the component in the column should be attached to the Data Structure Definition (i.e. one of `qb:dimension`, `qb:attribute`, `qb:measure` or nil)
- `property_template` - the predicate used to attach the (cell) values to the observations
- `value_template` - the URI template applied to the cell values
- `datatype` - as per csvw:datatype, how the cell value should be parsed (typically `string` for everything except the value column which will be `number`)
- `value-transformation` - WHAT DOES THIS DO? WHAT ARE THE ALLOWED OPTIONS

_check: is this right?_

This version of table2qb also includes several conventions in the code that ought to be generalised to configuration - particularly how cell values are slugged.


## Example

The [./examples/employment](./examples/employment) directory provides a full example and instructions for running it.

## How to compile table2qb

_fill in the details_

table2qb is written in Clojure

lein uberjar

where to get leiningen, any configuration of leiningen required?

need Java.  Any particular version?

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
