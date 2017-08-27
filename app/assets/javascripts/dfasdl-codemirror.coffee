root = exports ? this

# -------------------------------------------------------------------
# Helper functions
# -------------------------------------------------------------------
merge = (dest, objs...) ->
  for obj in objs
    dest[k] = v for k, v of obj
  dest

sortAttributes = (a) ->
  keys = Object.keys(a).sort()
  result = {}
  keys.forEach((key) ->
    result[key] = a[key]
  )
  result

values_byteorder = ["bigEndian", "littleEndian", "mittleEndian"]
values_decimal_separator = [".",",","⎖"]
values_encoding = ["big5", "big5-hkscs", "cesu-8", "euc-jp", "euc-kr", "gb18030", "gb2312", "gbk", "ibm-thai", "ibm00858", "ibm01140", "ibm01141", "ibm01142", "ibm01143", "ibm01144", "ibm01145", "ibm01146", "ibm01147", "ibm01148", "ibm01149", "ibm037", "ibm1026", "ibm1047", "ibm273", "ibm277", "ibm278", "ibm280", "ibm284", "ibm285", "ibm290", "ibm297", "ibm420", "ibm424", "ibm437", "ibm500", "ibm775", "ibm850", "ibm852", "ibm855", "ibm857", "ibm860", "ibm861", "ibm862", "ibm863", "ibm864", "ibm865", "ibm866", "ibm868", "ibm869", "ibm870", "ibm871", "ibm918", "iso-2022-cn", "iso-2022-jp", "iso-2022-jp-2", "iso-2022-kr", "iso-8859-1", "iso-8859-13", "iso-8859-15", "iso-8859-2", "iso-8859-3", "iso-8859-4", "iso-8859-5", "iso-8859-6", "iso-8859-7", "iso-8859-8", "iso-8859-9", "jis_x0201", "jis_x0212-1990", "koi8-r", "koi8-u", "shift_jis", "tis-620", "us-ascii", "utf-16", "utf-16be", "utf-16le", "utf-32", "utf-32be", "utf-32le", "utf-8", "windows-1250", "windows-1251", "windows-1252", "windows-1253", "windows-1254", "windows-1255", "windows-1256", "windows-1257", "windows-1258", "windows-31j", "x-big5-hkscs-2001", "x-big5-solaris", "x-compound_text", "x-euc-jp-linux", "x-euc-tw", "x-eucjp-open", "x-ibm1006", "x-ibm1025", "x-ibm1046", "x-ibm1097", "x-ibm1098", "x-ibm1112", "x-ibm1122", "x-ibm1123", "x-ibm1124", "x-ibm1166", "x-ibm1364", "x-ibm1381", "x-ibm1383", "x-ibm300", "x-ibm33722", "x-ibm737", "x-ibm833", "x-ibm834", "x-ibm856", "x-ibm874", "x-ibm875", "x-ibm921", "x-ibm922", "x-ibm930", "x-ibm933", "x-ibm935", "x-ibm937", "x-ibm939", "x-ibm942", "x-ibm942c", "x-ibm943", "x-ibm943c", "x-ibm948", "x-ibm949", "x-ibm949c", "x-ibm950", "x-ibm964", "x-ibm970", "x-iscii91", "x-iso-2022-cn-cns", "x-iso-2022-cn-gb", "x-iso-8859-11", "x-jis0208", "x-jisautodetect", "x-johab", "x-macarabic", "x-maccentraleurope", "x-maccroatian", "x-maccyrillic", "x-macdingbat", "x-macgreek", "x-machebrew", "x-maciceland", "x-macroman", "x-macromania", "x-macsymbol", "x-macthai", "x-macturkish", "x-macukraine", "x-ms932_0213", "x-ms950-hkscs", "x-ms950-hkscs-xp", "x-mswin-936", "x-pck", "x-sjis_0213", "x-utf-16le-bom", "x-utf-32be-bom", "x-utf-32le-bom", "x-windows-50220", "x-windows-50221", "x-windows-874", "x-windows-949", "x-windows-950", "x-windows-iso2022jp"]
values_mime = ["application", "audio", "image", "message", "multipart", "text", "video"]
values_trim = ["left", "right", "both"]
value_general = [""]

# -------------------------------------------------------------------
# Elementare Attributgruppen
# -------------------------------------------------------------------
attributes_elementary = {
  class: value_general,
  "correct-offset": value_general,
  encoding: values_encoding,
  s: value_general,
  "start-sign": value_general,
  "stop-sign": value_general
}

attributes_base = {
  id: value_general
}
merge(attributes_base, attributes_elementary)
attributes_base = sortAttributes(attributes_base)

attributes_database = {
  "db-insert": value_general,
  "db-primary-key": value_general,
  "db-select": value_general,
  "db-update": value_general
}

attributes_database_basic = {
  "db-column-name": value_general
}

attributes_database_numeric = {
  "db-auto-inc": [true, false]
}

attributes_xml = {
  "xml-attribute-name": value_general,
  "xml-attribute-parent": value_general
}

general_xml = {
  "xml-element-name": value_general
}

merge(attributes_base, general_xml)
attributes_base = sortAttributes(attributes_base)

# -------------------------------------------------------------------
# Element specific attribute groups
# -------------------------------------------------------------------
# SEQ
attributes_sequence = {
  filter: value_general,
  "keepID": [true, false],
  max: value_general,
  min: value_general
}
merge(attributes_sequence, attributes_base)
merge(attributes_sequence, attributes_database)
attributes_sequence = sortAttributes(attributes_sequence)

# FIXSEQ
attributes_fixed_sequence = {
  "keepID": [true, false],
  count: value_general
}
merge(attributes_fixed_sequence, attributes_base)
merge(attributes_fixed_sequence, attributes_database)
attributes_fixed_sequence = sortAttributes(attributes_fixed_sequence)

# Reference
attributes_reference = {
  sid: value_general,
  value: value_general
}
merge(attributes_reference, attributes_base)
attributes_reference = sortAttributes(attributes_reference)

# Numeric
attributes_numeric = {
  "db-foreign-key": value_general,
  defaultnum: value_general,
  length: value_general,
  "max-digits": value_general,
  precision: value_general
}
merge(attributes_numeric, attributes_base)
merge(attributes_numeric, attributes_database_basic)
merge(attributes_numeric, attributes_database_numeric)
merge(attributes_numeric, attributes_xml)
attributes_numeric = sortAttributes(attributes_numeric)

# Formatted Numeric
attributes_formatted_numeric = {
  "db-foreign-key": value_general,
  format: value_general,
  defaultnum: value_general,
  "decimal-separator": values_decimal_separator,
  "max-digits": value_general,
  "max-precision": value_general
}
merge(attributes_formatted_numeric, attributes_base)
merge(attributes_formatted_numeric, attributes_database_basic)
merge(attributes_numeric, attributes_database_numeric)
merge(attributes_formatted_numeric, attributes_xml)
attributes_formatted_numeric = sortAttributes(attributes_formatted_numeric)

# String
attributes_string = {
  "db-foreign-key": value_general,
  format: value_general,
  defaultstr: value_general,
  length: value_general,
  "max-length": value_general,
  trim: values_trim
}
merge(attributes_string, attributes_base)
merge(attributes_string, attributes_database_basic)
merge(attributes_string, attributes_xml)
attributes_string = sortAttributes(attributes_string)

# Formatted String
attributes_formatted_string = {
  "db-foreign-key": value_general,
  format: value_general
  defaultstr: value_general,
  trim: values_trim
}
merge(attributes_formatted_string, attributes_base)
merge(attributes_formatted_string, attributes_database_basic)
merge(attributes_formatted_string, attributes_xml)
attributes_formatted_string = sortAttributes(attributes_formatted_string)

# Date
attributes_date = {
  "db-foreign-key": value_general
}
merge(attributes_date, attributes_base)
merge(attributes_date, attributes_database_basic)
merge(attributes_date, attributes_xml)
attributes_date = sortAttributes(attributes_date)

# Time
attributes_time = {}
merge(attributes_time, attributes_base)
merge(attributes_time, attributes_database_basic)
merge(attributes_time, attributes_xml)
attributes_time = sortAttributes(attributes_time)

# Formatted Time
attributes_formatted_time = {
  "db-foreign-key": value_general,
  format: value_general,
  defaultstr: value_general
}
merge(attributes_formatted_time, attributes_base)
merge(attributes_formatted_time, attributes_database_basic)
merge(attributes_formatted_time, attributes_xml)
attributes_formatted_time = sortAttributes(attributes_formatted_time)

# Binary
attributes_binary = {
  byteOrder: values_byteorder,
  mime: values_mime
}
merge(attributes_binary, attributes_base)
merge(attributes_binary, attributes_database_basic)
attributes_binary = sortAttributes(attributes_binary)

# Binary64
attributes_binary64 = {
  mime: values_mime
}
merge(attributes_binary64, attributes_base)
merge(attributes_binary64, attributes_database_basic)
attributes_binary64 = sortAttributes(attributes_binary64)

# BinaryHex
attributes_binaryHex = {
  mime: values_mime
}
merge(attributes_binaryHex, attributes_base)
merge(attributes_binaryHex, attributes_database_basic)
attributes_binaryHex = sortAttributes(attributes_binaryHex)

# -------------------------------------------------------------------
# Elements
# -------------------------------------------------------------------

data_elements = ["bin", "bin64", "binHex", "formatnum", "formatstr", "num", "str"]

time_elements = ["date", "datetime", "formattime", "time"]

structural_elements = ["choice", "cid", "elem", "fixseq", "ref", "seq"]

choice_structural_elements = ["cid", "celem"]

expression_elements = ["const", "sxp"]

choice_elements = []
choice_elements = choice_elements.concat(choice_structural_elements).concat(data_elements).concat(time_elements)
choice_elements.sort()

elements = []
elements = elements.concat(structural_elements).concat(data_elements).concat(time_elements).concat(expression_elements)
elements.sort()

$ ->
  tags = {
    "!top": ["dfasdl"],
    "!attrs": {},
    dfasdl: {
      attrs: {
        "default-encoding": values_encoding,
        semantic: ["custom"],
        "start-sign": null,
        "stop-sign": null,
        xmlns: ["http://www.dfasdl.org/DFASDL"]
      },
      children: elements
    },
    bin: {
      attrs: attributes_binary,
      children: []
    },
    bin64: {
      attrs: attributes_binary64,
      children: []
    },
    binHex: {
      attrs: attributes_binaryHex,
      children: []
    },
    celem: {
      attrs: attributes_base,
      children: choice_elements
    },
    choice: {
      attrs: attributes_base
      children: ["celem"]
    },
    cid: {
      attrs: attributes_base,
      children: data_elements
    },
    const: {
      attrs: attributes_base,
      children: elements
    },
    date: {
      attrs: attributes_date,
      children: []
    },
    datetime: {
      attrs: attributes_date,
      children: []
    },
    elem: {
      attrs: attributes_base,
      children: elements
    },
    fixseq: {
      attrs: attributes_fixed_sequence,
      children: structural_elements
    },
    formatnum: {
      attrs: attributes_formatted_numeric,
      children: []
    },
    formatstr: {
      attrs: attributes_formatted_string,
      children: []
    },
    formattime: {
      attrs: attributes_formatted_time,
      children: []
    },
    num: {
      attrs: attributes_numeric,
      children: []
    },
    ref: {
      attrs: attributes_reference,
      children: elements
    },
    seq: {
      attrs: attributes_sequence,
      children: structural_elements
    },
    str: {
      attrs: attributes_string,
      children: []
    },
    sxp: {
      attrs: attributes_base,
      children: []
    },
    time: {
      attrs: attributes_time,
      children: []
    }
  }

  completeAfterSign = (command, pred) ->
    cur = command.getCursor()
    if(!pred || pred())
      setTimeout((->
        if(!command.state.completionActive)
          command.showHint({completeSingle: false})
      ), 100)
    CodeMirror.Pass

  completeIfAfterBackslash = (command) ->
    completeAfterSign(command, (->
      cur = command.getCursor()
      command.getRange(CodeMirror.Pos(cur.line, cur.ch - 1), cur) == "<"
    ))

  completeIfInAttribute = (command) ->
    completeAfterSign(command, (->
      tok = command.getTokenAt(command.getCursor())
      if (tok.type == "string" && (!/['"]/.test(tok.string.charAt(tok.string.length - 1)) || tok.string.length == 1))
        false
      inner = CodeMirror.innerMode(command.getMode, tok.state).state
      inner.tagName
    ))

  fol = (command) ->
    command.foldCode(command.getCursor())

  toFullscreen = (command) ->
    command.setOption("fullScreen", !command.getOption("fullScreen"))

  escapeFullScreen = (command) ->
    if (command.getOption("fullScreen"))
      command.setOption("fullScreen", false)

  #
  # Die editierbare DFASDL
  #
  #
  if (document.getElementById('dfasdl.content'))
    editor = CodeMirror.fromTextArea(document.getElementById('dfasdl.content'), {
      autoCloseBrackets: true,
      autoCloseTags: true,
      matchTags: true,
      showTrailingSpace: true,
      extraKeys: {
        "'<'": completeAfterSign,
        "'/'": completeIfAfterBackslash,
        "' '": completeIfInAttribute,
        "'='": completeIfInAttribute,
        "F11": toFullscreen,
        "Esc": escapeFullScreen,
        'Ctrl-Q': fol,
        "Ctrl-Space": 'autocomplete'
      },
      highlightSelectionMatches: true,
      hintOptions: {
        schemaInfo: tags
      },
      lineNumbers: true,
      mode: 'xml',
      styleActiveLine: true,
      lineWrapping: true,
      foldGutter: true,
      gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
      theme: 'solarized light'
    })
    editor.setCursor(2)

  #
  # Die deaktivierte DFASDL
  #
  #
  if (document.getElementById('dfasdlDeactivated.content'))
    editor = CodeMirror.fromTextArea(document.getElementById('dfasdlDeactivated.content'), {
      autoCloseBrackets: true,
      autoCloseTags: true,
      matchTags: true,
      showTrailingSpace: true,
      extraKeys: {
        "'<'": completeAfterSign,
        "'/'": completeIfAfterBackslash,
        "' '": completeIfInAttribute,
        "'='": completeIfInAttribute,
        "F11": toFullscreen,
        "Esc": escapeFullScreen,
        'Ctrl-Q': fol,
        "Ctrl-Space": 'autocomplete'
      },
      highlightSelectionMatches: true,
      hintOptions: {
        schemaInfo: tags
      },
      lineNumbers: true,
      mode: 'xml',
      styleActiveLine: true,
      lineWrapping: true,
      foldGutter: true,
      gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
      theme: 'solarized light',
      readOnly: true
    })
    editor.setCursor(2)

  #
  # Die DFASDL mit dem Diff
  #
  #
  if (document.getElementById('dfasdlDiffDeactivated.content'))
    editor = CodeMirror.fromTextArea(document.getElementById('dfasdlDiffDeactivated.content'), {
      autoCloseBrackets: true,
      autoCloseTags: true,
      matchTags: true,
      showTrailingSpace: true,
      extraKeys: {
        "'<'": completeAfterSign,
        "'/'": completeIfAfterBackslash,
        "' '": completeIfInAttribute,
        "'='": completeIfInAttribute,
        "F11": toFullscreen,
        "Esc": escapeFullScreen,
        'Ctrl-Q': fol,
        "Ctrl-Space": 'autocomplete'
      },
      highlightSelectionMatches: true,
      hintOptions: {
        schemaInfo: tags
      },
      lineNumbers: true,
      mode: 'xml',
      styleActiveLine: true,
      lineWrapping: true,
      foldGutter: true,
      gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
      theme: 'solarized light',
      readOnly: true,
      styleSelectedText: true
    })
    if(diffs)
      diffs.forEach((lNumber) ->
        editor.markText({line: lNumber, ch: 0}, {line: lNumber, ch: 2000}, {className: "styled-background"})
      )

  root.validateDFASDL = ->
    dfasdl = editor.getValue()
    oParser = new DOMParser()
    oDOM = oParser.parseFromString(dfasdl, 'text/xml')
    # Validierung nur durchführen, wenn das XML wohlgeformt ist
    if (oDOM.documentElement.nodeName == "parsererror")
      document.getElementById("notWellFormed").setAttribute("class", "text-danger")
      document.getElementById("validationResult").setAttribute("class", "hidden")
      document.getElementById("errorMessage").setAttribute("class", "hidden")
    else
      document.getElementById("notWellFormed").setAttribute("class", "hidden")
      document.getElementById("validationResult").setAttribute("class", "")
      document.getElementById("errorMessage").setAttribute("class", "hidden")
      validate(dfasdl)

  validate = (content) ->
    requestUrl = jsRoutes.controllers.DFASDLResourcesController.validateDFASDL().url
    $.ajax requestUrl,
      type: "POST"
      contentType: "text/xml;charset=UTF-8"
      dataType: "json"
      data: content
      error: (jqXHR, textStatus, errorThrown) ->
        result = jqXHR.responseJSON
        updateNode = document.createElement("strong")
        if (result?)
          updateNode.innerHTML = result.message
          updateNode.setAttribute("class", result.class)
          element = document.getElementById("errorMessage")
          element.setAttribute("class", "alert alert-danger")
          element.innerHTML = result.error
        else
          updateNode.innerHTML = "An error occured while trying to validate the DFASDL!"
          updateNode.setAttribute("class", "text-danger")
          element = document.getElementById("errorMessage")
          element.setAttribute("class", "alert alert-danger")
          element.innerHTML = errorThrown
        placeNode("validationResult", updateNode)
      success: (data, textStatus, jqXHR) ->
        result = data
        updateNode = document.createElement("strong")
        updateNode.innerHTML = result.message
        updateNode.setAttribute("class", result.class)
        placeNode("validationResult", updateNode)
        if (result.error != "")
          element = document.getElementById("errorMessage")
          element.setAttribute("class", "alert alert-warning")
          element.innerHTML = result.error

  placeNode = (id, node) ->
    element = document.getElementById(id)
    if (element != null)
      element.innerHTML = ""
      element.appendChild(node)

