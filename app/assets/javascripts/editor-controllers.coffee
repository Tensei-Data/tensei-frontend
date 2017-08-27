###
  Controllers for the editor application.
###

Editor.CookbookresourcesMappingsController = Ember.Controller.extend({
  newRecipeNumber: 0,
  # the following variable is a counter that will be increased if a change occurred on the page
  # this is necessary to detect changes at the recipes, transformations, etc. that will only be
  # saved when the user clicks on the save button
  changes: 0,
  # indicates whether the user clicked the save button
  saved: 0,
  #
  changesElementsForDisplay: 0,
  actions: {
    addAtomicTransformation: (recipe, mapping) ->
      recipe.mappings.removeObject(mapping)
      selectedTransformation = this.get('selectedAtomicTransformation')
      name = this.get("availableTransformationsClassNames")[selectedTransformation.transformerClassName]
      options = this.get('selectedAtomicTransformerOptions')
      transformerParams= []
      for k,v of options
        if (options.hasOwnProperty(k))
          if (v.value != null && v.value != undefined)
            transformerParams.push([k, v.value])
          else
            transformerParams.push([k, ""])
      elementRef = EditorClasses.createElementReference(selectedTransformation.element.dfasdlId, selectedTransformation.element.elementId)
      optionsObject = EditorClasses.createTransformerOption("java.lang.String", "java.lang.String", transformerParams)
      selectedTransformation = EditorClasses.createAtomicTransformationDescription(elementRef, name, optionsObject)

      mapping.atomicTransformations.pushObject(selectedTransformation)
      recipe.mappings.pushObject(mapping)
      this.set('selectedAtomicTransformation', null)
      this.set('selectedAtomicTransformerOptions', null)
      this.set('selectedTransformerOptionValue', {})
      this.set('changes', this.get('changes') + 1)
    addTransformation: (recipe, mapping) ->
      recipe.mappings.removeObject(mapping)
      selectedTransformation = this.get('selectedTransformation')
      name = this.get("availableTransformationsClassNames")[selectedTransformation.transformerClassName]
      options = this.get('selectedTransformerOptions')
      transformerParams= []
      for k,v of options
        if (options.hasOwnProperty(k))
          if (v.value != null && v.value != undefined)
            transformerParams.push([k, v.value])
          else
            transformerParams.push([k, ""])
      optionsObject = EditorClasses.createTransformerOption("java.lang.String", "java.lang.String", transformerParams)
      selectedTransformation = EditorClasses.createTransformationDescription(name, optionsObject)

      mapping.transformations.pushObject(selectedTransformation)
      recipe.mappings.pushObject(mapping)
      this.set('selectedTransformation', null)
      this.set('selectedTransformer', null)
      this.set('selectedTransformerOptions', null)
      this.set('selectedTransformerOptionValue', {})
      this.set('changes', this.get('changes') + 1)
    cancelAtomicTransformation: ->
      this.set('selectedAtomicTransformation', null)
      this.set('selectedAtomicTransformer', null)
      this.set('selectedAtomicTransformerOptions', null)
    cancelMapping: ->
      this.set('selectedMapping', null)
    cancelRecipe: (recipe) ->
      if (recipe.mappings.length == 0)
        this.get('recipes').removeObject(recipe)
      this.set('selectedRecipe', { id: null })
      this.set('selectedMapping', null)
    cancelTransformation: ->
      this.set('selectedTransformation', null)
      this.set('selectedTransformer', null)
      this.set('selectedTransformerOptions', null)
      this.set('selectedTransformerOptionValue', {})
    clickedSourceElementsForDisplay: ->
      options = $('select#sourceIDElementsForDisplay option')
      sourceElementsDisabled = []
      sourceElements = []
      (
        dfasdlId = option.title
        elementId = option.text
        selected = option.selected

        elem = EditorClasses.createElementToDisplay(elementId, dfasdlId, selected)
        sourceElements.push(elem)
        if (!selected)
          sourceElementsDisabled.push(elem)
      ) for option in options

      this.set('sourceElementsDisabled', sourceElementsDisabled)
      this.set('availableSourceElementsForDisplay', sourceElements)
      this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    clickedTargetElementsForDisplay: ->
      options = $('select#targetIDElementsForDisplay option')
      targetElementsDisabled = []
      targetElements = []
      (
        dfasdlId = option.title
        elementId = option.text
        selected = option.selected

        elem = EditorClasses.createElementToDisplay(elementId, dfasdlId, selected)
        targetElements.push(elem)
        if (!selected)
          targetElementsDisabled.push(elem)
      ) for option in options

      this.set('targetElementsDisabled', targetElementsDisabled)
      this.set('availableTargetElementsForDisplay', targetElements)
      this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    deleteAtomicTransformation: (transformation) ->
      mapping = this.get('selectedMapping')
      mapping.atomicTransformations.removeObject(transformation)
      this.set('changes', this.get('changes') + 1)
    deleteMapping: (recipe, mapping) ->
      recipe.mappings.removeObject(mapping)
      this.set('selectedMapping', null)
      this.set('changes', this.get('changes') + 1)
    deleteRecipe: (recipe) ->
      this.get('recipes').removeObject(recipe)
      this.set('selectedRecipe', { id: null })
      this.set('selectedMapping', null)
      this.set('changes', this.get('changes') + 1)
    deleteSourceKey: (key) ->
      selectedMapping = this.get('selectedMapping')
      if(selectedMapping != null)
        sources = []
        $('#sortableSourceIds').find('.item').each(() ->
          if ($(this).data('id') != key)
            sources.push($(this).data('id'))
        )
        selectedMapping.sources = sources
        this.set('changes', this.get('changes') + 1)
    deleteTargetKey: (key) ->
      selectedMapping = this.get('selectedMapping')
      if(selectedMapping != null)
        targets = []
        $('#sortableTargetIds').find('.item').each(() ->
          if ($(this).data('id') != key)
            targets.push($(this).data('id'))
        )
        selectedMapping.targets = targets
        this.set('changes', this.get('changes') + 1)
    deleteTransformation: (transformation) ->
      mapping = this.get('selectedMapping')
      mapping.transformations.removeObject(transformation)
      this.set('changes', this.get('changes') + 1)
    getAdvancedSemanticSuggest: ->
      model = this.get('model')
      controller = this
      EditorHelpers.requestAdvancedSemanticSuggest(model.cookbookresource.get('id')).then((result) ->
        controller.set("recipes", [])
        newRecipes = result.recipes
        newRecipes.forEach((recipe) ->
          mappings = []
          recipe.mappings.forEach((mapping) ->
            # FIXME: wir schreiben einen leeren String als key; normalerweise sollte es die MappingKeyFieldDefinition nicht geben, wenn kein Key ausgewählt worden ist
            mappingKeyObject = EditorClasses.createMappingKeyFieldDefinition("")
            if (mapping.mappingKey != null && mapping.mappingKey.name != null)
              mappingKeyObject.name = mapping.mappingKey.name
            newMapping = EditorClasses.createMappingTransformation(mapping.sources, mapping.targets, mapping.transformations, mapping.atomicTransformations, mappingKeyObject)
            mappings.pushObject(newMapping)
          )
          recipe.mappings = mappings
          controller.get('recipes').pushObject(recipe)
        )
        controller.set('changes', controller.get('changes') + 1)
      )
    getSemanticSuggest: ->
      model = this.get('model')
      controller = this
      EditorHelpers.requestSemanticSuggest(model.cookbookresource.get('id')).then((result) ->
        controller.set("recipes", [])
        newRecipes = result.recipes
        newRecipes.forEach((recipe) ->
          mappings = []
          recipe.mappings.forEach((mapping) ->
            # FIXME: wir schreiben einen leeren String als key; normalerweise sollte es die MappingKeyFieldDefinition nicht geben, wenn kein Key ausgewählt worden ist
            mappingKeyObject = EditorClasses.createMappingKeyFieldDefinition("")
            if (mapping.mappingKey != null && mapping.mappingKey.name != null)
              mappingKeyObject.name = mapping.mappingKey.name
            newMapping = EditorClasses.createMappingTransformation(mapping.sources, mapping.targets, mapping.transformations, mapping.atomicTransformations, mappingKeyObject)
            mappings.pushObject(newMapping)
          )
          recipe.mappings = mappings
          controller.get('recipes').pushObject(recipe)
        )
        controller.set('changes', controller.get('changes') + 1)
      )
    getSimpleSuggest: ->
      model = this.get('model')
      controller = this
      EditorHelpers.requestSimpleSuggest(model.cookbookresource.get('id')).then((result) ->
        controller.set("recipes", [])
        newRecipes = result.recipes
        newRecipes.forEach((recipe) ->
          mappings = []
          recipe.mappings.forEach((mapping) ->
            # FIXME: wir schreiben einen leeren String als key; normalerweise sollte es die MappingKeyFieldDefinition
            # nicht geben, wenn kein Key ausgewählt worden ist
            mappingKeyObject = EditorClasses.createMappingKeyFieldDefinition("")
            if (mapping.mappingKey != null && mapping.mappingKey.name != null)
              mappingKeyObject.name = mapping.mappingKey.name
            newMapping = EditorClasses.createMappingTransformation(mapping.sources, mapping.targets, mapping.transformations, mapping.atomicTransformations, mappingKeyObject)
            mappings.pushObject(newMapping)
          )
          recipe.mappings = mappings
          controller.get('recipes').pushObject(recipe)
        )
        controller.set('changes', controller.get('changes') + 1)
      )
    newAtomicTransformation: ->
      elementRef = EditorClasses.createElementReference("", "")
      options = EditorClasses.createTransformerOption("java.lang.String", "java.lang.String", [])
      transformation = EditorClasses.createAtomicTransformationDescription(elementRef, "", options)
      this.set('selectedAtomicTransformation', transformation)
    newMapping: ->
      # FIXME: wir schreiben einen leeren String als key; normalerweise sollte es die MappingKeyFieldDefinition
      # nicht geben, wenn kein Key ausgewählt worden ist
      mappingKeyObject = EditorClasses.createMappingKeyFieldDefinition("")
      mapping = EditorClasses.createMappingTransformation([], [], [], [], mappingKeyObject)
      this.set('selectedMapping', mapping)
    newRecipe: ->
      # if we already have recipes with names like RECIPE-1, RECIPE-2, RECIPE-3 and edit the current cookbook
      # at a later time, we must find the correct count for creating a new recipe with the name RECIPE-4
      recipes = this.get('recipes')
      recipeExists = true
      currentRecipeNumber = (this.get('newRecipeNumber') - 1)
      loop
        currentRecipeNumber = currentRecipeNumber + 1
        foundRecipe = recipes.filter (r) ->
          r.id == "RECIPE-" + (currentRecipeNumber + 1)
        if (foundRecipe.length == 0)
          recipeExists = false
        break if (!recipeExists)
      this.set('newRecipeNumber', currentRecipeNumber)

      recipe = EditorClasses.createRecipe('RECIPE-' + (this.get('newRecipeNumber') + 1), 'MapAllToAll', [])
      this.get('recipes').pushObject(recipe)
      this.set('selectedMapping', null)
      this.set('selectedRecipe', recipe)
    newTransformation: ->
      options = EditorClasses.createTransformerOption("java.lang.String", "java.lang.String", [])
      transformation = EditorClasses.createTransformationDescription("", options)
      selectedTransformerIs = this.get('selectedTransformerIs')
      this.set('selectedTransformation', transformation)
    saveMapping: (recipe, selectedMapping) ->
      if(selectedMapping != null)
        recipe.mappings.removeObject(selectedMapping)
        transformationsList = []
        oldTransformatins = selectedMapping.transformations
        $('#sortableTransformations').find('.item').each(() ->
          position = $(this).data('id')
          if(oldTransformatins.length > position)
            transformationsList.push(oldTransformatins.get(position))
        )
        Ember.set(selectedMapping, 'transformations', transformationsList)

        atomicTransformationsList = []
        oldAtomicTransformatins = selectedMapping.atomicTransformations
        $('#sortableAtomicTransformations').find('.item').each(() ->
          position = $(this).data('id')
          if(oldAtomicTransformatins.length > position)
            atomicTransformationsList.push(oldAtomicTransformatins.get(position))
        )
        Ember.set(selectedMapping, 'atomicTransformations', atomicTransformationsList)

        sources = []
        $('#sortableSourceIds').find('.item').each(() ->
          sourceElementReference = EditorClasses.createElementReference($(this).data('dfasdlId'), $(this).data('id'))
          sources.push(sourceElementReference)
        )
        Ember.set(selectedMapping, 'sources', sources)

        targets = []
        $('#sortableTargetIds').find('.item').each(() ->
          targetElementReference = EditorClasses.createElementReference($(this).data('dfasdlId'), $(this).data('id'))
          targets.push(targetElementReference)
        )
        Ember.set(selectedMapping, 'targets', targets)
        recipe.mappings.pushObject(selectedMapping)

        this.set('changes', this.get('changes') + 1)
      this.set('selectedMapping', null)
    selectAllSourceElementsForDisplay: ->
      elements = this.get('availableSourceElementsForDisplay')
      if (elements)
        (element.selected = true) for element in elements
        elements.setObjects(elements)
        this.set('sourceElementsDisabled', [])
        this.set('availableSourceElementsForDisplay', elements)
        this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    deselectAllSourceElementsForDisplay: ->
      elements = this.get('availableSourceElementsForDisplay')
      if (elements)
        (element.selected = false) for element in elements
        elements.setObjects(elements)
        this.set('sourceElementsDisabled', elements)
        this.set('availableSourceElementsForDisplay', elements)
        this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    selectAllTargetElementsForDisplay: ->
      elements = this.get('availableTargetElementsForDisplay')
      if (elements)
        (element.selected = true) for element in elements
        elements.setObjects(elements)
        this.set('targetElementsDisabled', [])
        this.set('availableTargetElementsForDisplay', elements)
        this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    selectOnlyMappedElementsForDisplay: ->
      recipes = this.get('recipes')
      # Collect the source and target IDs from the mappings
      sourceIds = []
      targetIds = []
      recipes.forEach((recipe) ->
        if (recipe.mappings)
          recipe.mappings.forEach((mapping) ->
            sourceIds = sourceIds.concat mapping.sources
            targetIds = targetIds.concat mapping.targets
          )
      )

      if (sourceIds.length > 0 && targetIds.length > 0)
        # Determine the IDs of the sequence elements that should be shown
        model = this.get('model')
        myCookbook = model.cookbookresource.get('cookbook')
        sourceIdsSequences = []
        targetIdsSequences = []
        if (window.DOMParser)
          parser = new DOMParser()
          # sources
          myCookbook.sources.forEach((source) ->
            xml = parser.parseFromString(source.content, 'text/xml')
            if (xml)
              sourceIds.forEach((sourceId) ->
                if (source.id == sourceId.dfasdlId)
                  sourceIdNode = xml.getElementById(sourceId.elementId)
                  if (sourceIdNode)
                    parentSeq = getSequenceParent(sourceIdNode)
                    if (parentSeq != undefined && parentSeq.getAttribute("id"))
                      sourceIdsSequences.push({dfasdlId: source.id, elementId: parentSeq.getAttribute("id")})
              )
          )
          #targets
          xml = parser.parseFromString(myCookbook.target.content, 'text/xml')
          if (xml)
            targetIds.forEach((target) ->
              if (myCookbook.target.id == target.dfasdlId)
                targetIdNode = xml.getElementById(target.elementId)
                if (targetIdNode)
                  parentSeq = getSequenceParent(targetIdNode)
                  if (parentSeq != undefined && parentSeq.getAttribute("id"))
                    targetIdsSequences.push({dfasdlId: myCookbook.target.id, elementId: parentSeq.getAttribute("id")})
             )

        if(sourceIdsSequences.length > 0)
          # Select the appropriate source elements
          sourceElements = this.get('availableSourceElementsForDisplay')

          if (sourceElements)
            runner = 0
            sourceElementsDisabled = []
            while (runner < sourceElements.length)
              if (EditorHelpers.elementExistsInElementReferences(sourceElements[runner].name, sourceElements[runner].dfasdlId, sourceIdsSequences) != undefined)
                sourceElements[runner].selected = true
              else
                sourceElements[runner].selected = false
                elem = EditorClasses.createElementToDisplay(sourceElements[runner].name, sourceElements[runner].dfasdlId, false)
                sourceElementsDisabled.push(elem)
              runner = runner + 1

            sourceElements.setObjects(sourceElements)
            this.set('sourceElementsDisabled', sourceElementsDisabled)
            this.set('availableSourceElementsForDisplay', sourceElements)
          # Select the appropriate target elements
          targetElements = this.get('availableTargetElementsForDisplay')
          if (targetElements)
            runner = 0
            targetElementsDisabled = []
            while (runner < targetElements.length)
              if (EditorHelpers.elementExistsInElementReferences(targetElements[runner].name, targetElements[runner].dfasdlId, targetIdsSequences) != undefined)
                targetElements[runner].selected = true
              else
                targetElements[runner].selected = false
                elem = EditorClasses.createElementToDisplay(targetElements[runner].name, targetElements[runner].dfasdlId, false)
                targetElementsDisabled.push(elem)
              runner = runner + 1
            targetElements.setObjects(targetElements)
            this.set('targetElementsDisabled', targetElementsDisabled)
            this.set('availableTargetElementsForDisplay', targetElements)

          this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    deselectAllTargetElementsForDisplay: ->
      elements = this.get('availableTargetElementsForDisplay')
      if (elements)
        (element.selected = false) for element in elements
        elements.setObjects(elements)
        this.set('targetElementsDisabled', elements)
        this.set('availableTargetElementsForDisplay', elements)
        this.set('changesElementsForDisplay', this.get('changesElementsForDisplay') + 1)
    selectMapping: (mappingTransformation) ->
      if (this.get('selectedMapping') == mappingTransformation)
        this.set('selectedMapping', null)
      else
        this.set('selectedMapping', mappingTransformation)
    selectRecipe: (recipe) ->
      this.set('selectedMapping', null)
      if (this.get('selectedRecipe') == recipe)
        this.set('selectedRecipe', { id: null })
      else
        this.set('selectedRecipe', recipe)
  },
  cookbook: (->
    dfasdls = {
      sources: [],
      target: null
    }
    model = this.get('model')
    myCookbook = model.cookbookresource.get('cookbook')

    myCookbook.sources.map (source) ->
      dfasdls.sources.push({id: source.id, content: jQuery.parseXML(source.content)})
    if (myCookbook.target)
      dfasdls.target = {id: myCookbook.target.id, content: jQuery.parseXML(myCookbook.target.content)}
    dfasdls
  ).property("model.cookbookresource.cookbook"),
  mustBeSaved: (->
    if (this.get('changes') == 1)
      this.set('saved', 0)
    this.get('changes') > 0 && this.get('mappingsSaving') == 0
  ).property('changes'),
  hasBeenSaved: (->
    this.get('saved')
  ).property('saved'),
  hasSelectedAtomicTransformation: (->
    this.get('selectedAtomicTransformation') != null
  ).property('selectedAtomicTransformation'),
  hasSelectedMapping: (->
    this.get('selectedMapping') != null
  ).property('selectedMapping')
  hasSelectedRecipe: (->
    this.get('selectedRecipe').id != null
  ).property('selectedRecipe'),
  hasSelectedTransformation: (->
    this.get('selectedTransformation') != null
  ).property('selectedTransformation'),
  isSelectedAtomicTransformer: (->
    if (this.get('selectedAtomicTransformer'))
      properties = this.get('availableAtomicTransformerOptions')
      properties = properties[this.get('selectedAtomicTransformer')]
      if (properties)
        this.set('selectedAtomicTransformerOptions', properties)
    this.get('selectedAtomicTransformer') != null
  ).property('selectedAtomicTransformer'),
  isSelectedTransformer: (->
    if (this.get('selectedTransformer'))
      properties = this.get('availableTransformerOptions')
      properties = properties[this.get('selectedTransformer')]
      if (properties)
        this.set('selectedTransformerOptions', properties)
    this.get('selectedTransformer') != null
  ).property('selectedTransformer'),
  selectedAtomicTransformer: null,
  selectedAtomicTransformerIs: (->
    ts = {
      BoxDataIntoList: false,
      Replace: false,
      TimestampAdjuster: false
    }
    t = this.get('selectedAtomicTransformer')
    if (t != null)
      ts[t] = true

    ts
  ).property('selectedAtomicTransformer'),
  selectedTransformer: null,
  selectedTransformerIs: (->
    ts = {
      CastStringToLong: false,
      Concat: false,
      DateConverter: false,
      DateTypeConverter: false,
      DateValueToString: false,
      DrupalVanCodeTransformer: false,
      EmptyString: false,
      ExtractBiggestValue: false,
      IDTransformer: false,
      IfThenElseNumeric: false,
      LowerOrUpper: false,
      MergeAndExtractByRegEx: false,
      Nullify: false,
      Overwrite: false,
      Replace: false,
      Split: false,
      TimestampCalibrate: false
    }
    t = this.get('selectedTransformer')
    if (t != null)
      ts[t] = true

    ts
  ).property('selectedTransformer'),
  selectedTransformerOptionValue: {},
  selectedAtomicTransformerOptions: null,
  selectedTransformerOptions: null,
  recipeModes: ["MapAllToAll", "MapOneToOne"],
  availableAtomicTransformerOptions:{
    BoxDataIntoList: {},
    Replace: {
      count: {
        options: null,
        value: ""
      },
      replace: {
        options: null,
        value: ""
      },
      search: {
        options: null,
        value: ""
      }
    },
    TimestampAdjuster: {
      perform: {
        options: ["add", "reduce"],
        value: null
      }
    },
  },
  availableTransformerOptions:{
    CastStringToLong: {},
    Concat: {
      prefix: {
        options: null,
        value: ""
      },
      separator: {
        options: null,
        value: ""
      },
      suffix: {
        options: null,
        value: ""
      }
    },
    DateConverter: {
      format: {
        options: null,
        value: ""
      },
      timezone: {
        options: null,
        value: ""
      }
    },
    DateTypeConverter: {
      target: {
        options: ["date", "datetime", "time"],
        value: null
      }
    },
    DateValueToString: {
      format: {
        options: null,
        value: ""
      }
    },
    DrupalVanCodeTransformer: {},
    EmptyString: {},
    ExtractBiggestValue: {},
    IDTransformer: {
      field: {
        options: null,
        value: ""
      },
      start: {
        options: null,
        value: ""
      },
      type: {
        options: ["long", "uuid"],
        value: null
      }
    },
    IfThenElseNumeric: {
      else: {
        options: null,
        value: ""
      },
      format: {
        options: ["dec", "num"],
        value: null
      },
      if: {
        options: null,
        value: ""
      },
      then: {
        options: null,
        value: ""
      },
    },
    LowerOrUpper: {
      locale: {
        options: ["", "und", "ar", "ar-AE", "ar-BH", "ar-DZ", "ar-EG", "ar-IQ", "ar-JO", "ar-KW", "ar-LB", "ar-LY", "ar-MA", "ar-OM", "ar-QA", "ar-SA", "ar-SD", "ar-SY", "ar-TN", "ar-YE", "be", "be-BY", "bg", "bg-BG", "ca", "ca-ES", "cs", "cs-CZ", "da", "da-DK", "de", "de-AT", "de-CH", "de-DE", "de-GR", "de-LU", "el", "el-CY", "el-GR", "en", "en-AU", "en-CA", "en-GB", "en-IE", "en-IN", "en-MT", "en-NZ", "en-PH", "en-SG", "en-US", "en-ZA", "es", "es-AR", "es-BO", "es-CL", "es-CO", "es-CR", "es-CU", "es-DO", "es-EC", "es-ES", "es-GT", "es-HN", "es-MX", "es-NI", "es-PA", "es-PE", "es-PR", "es-PY", "es-SV", "es-US", "es-UY", "es-VE", "et", "et-EE", "fi", "fi-FI", "fr", "fr-BE", "fr-CA", "fr-CH", "fr-FR", "fr-LU", "ga", "ga-IE", "he", "he-IL", "hi", "hi-IN", "hr", "hr-HR", "hu", "hu-HU", "id", "id-ID", "is", "is-IS", "it", "it-CH", "it-IT", "ja", "ja-JP", "ja-JP-u-ca-japanese-x-lvariant-JP", "ko", "ko-KR", "lt", "lt-LT", "lv", "lv-LV", "mk", "mk-MK", "ms", "ms-MY", "mt", "mt-MT", "nl", "nl-BE", "nl-NL", "nn-NO", "no", "no-NO", "pl", "pl-PL", "pt", "pt-BR", "pt-PT", "ro", "ro-RO", "ru", "ru-RU", "sk", "sk-SK", "sl", "sl-SI", "sq", "sq-AL", "sr", "sr-BA", "sr-CS", "sr-Latn", "sr-Latn-BA", "sr-Latn-ME", "sr-Latn-RS", "sr-ME", "sr-RS", "sv", "sv-SE", "th", "th-TH", "th-TH-u-nu-thai-x-lvariant-TH", "tr", "tr-TR", "uk", "uk-UA", "vi", "vi-VN", "zh", "zh-CN", "zh-HK", "zh-SG", "zh-TW"],
        value: null
      },
      perform: {
        options: ["lower", "upper", "firstlower", "firstupper"],
        value: null
      }
    },
    MergeAndExtractByRegEx: {
      filler: {
        options: null,
        value: ""
      },
      groups: {
        options: null,
        value: ""
      },
      regexp: {
        options: null,
        value: ""
      }
    },
    Nullify: {},
    Overwrite: {
      type: {
        options: ["bigdecimal", "byte", "date", "datetime", "long", "none", "string", "time"],
        value: null
      },
      value: {
        options: null,
        value: ""
      }
    },
    Replace: {
      count: {
        options: null,
        value: ""
      },
      replace: {
        options: null,
        value: ""
      },
      search: {
        options: null,
        value: ""
      }
    },
    Split: {
      limit: {
        options: null,
        value: ""
      },
      pattern: {
        options: null,
        value: ""
      },
      selected: {
        options: null,
        value: ""
      }
    },
    TimestampCalibrate: {
      perform: {
        options: ["add", "reduce"],
        value: null
      }
    },
  },
  availableSourceElementsForDisplay: [],
  sourceElementsDisabled: [],
  availableTargetElementsForDisplay: [],
  targetElementsDisabled: [],
  availableMappingKeys: [],
  availableSourceIds: [],
  availableAtomicTransformations: ["", "BoxDataIntoList", "Replace", "TimestampAdjuster"],
  availableTransformations: ["", "CastStringToLong", "Concat", "DateConverter", "DateTypeConverter", "DateValueToString", "DrupalVanCodeTransformer", "EmptyString", "ExtractBiggestValue", "IDTransformer", "IfThenElseNumeric", "LowerOrUpper", "MergeAndExtractByRegEx", "Nullify", "Overwrite", "Replace", "Split", "TimestampCalibrate"],
  availableTransformationsClassNames: {"BoxDataIntoList": "com.wegtam.tensei.agent.transformers.atomic.BoxDataIntoList","CastStringToLong": "com.wegtam.tensei.agent.transformers.CastStringToLong", "Concat": "com.wegtam.tensei.agent.transformers.Concat","DateConverter": "com.wegtam.tensei.agent.transformers.DateConverter","DateTypeConverter":"com.wegtam.tensei.agent.transformers.DateTypeConverter", "DateValueToString":"com.wegtam.tensei.agent.transformers.DateValueToString","DrupalVanCodeTransformer":"com.wegtam.tensei.agent.transformers.DrupalVanCodeTransformer","EmptyString":"com.wegtam.tensei.agent.transformers.EmptyString","ExtractBiggestValue": "com.wegtam.tensei.agent.transformers.ExtractBiggestValue","IDTransformer": "com.wegtam.tensei.agent.transformers.IDTransformer","IfThenElseNumeric": "com.wegtam.tensei.agent.transformers.IfThenElseNumeric","LowerOrUpper":"com.wegtam.tensei.agent.transformers.LowerOrUpper","MergeAndExtractByRegEx": "com.wegtam.tensei.agent.transformers.MergeAndExtractByRegEx","Nullify": "com.wegtam.tensei.agent.transformers.Nullify","Overwrite":"com.wegtam.tensei.agent.transformers.Overwrite","Replace": "com.wegtam.tensei.agent.transformers.Replace","Split": "com.wegtam.tensei.agent.transformers.Split","TimestampAdjuster": "com.wegtam.tensei.agent.transformers.atomic.TimestampAdjuster","TimestampCalibrate": "com.wegtam.tensei.agent.transformers.TimestampCalibrate"},
  availableTypes: [{label: "String", id: "java.lang.String"}, {label: "Integer", id: "java.lang.Integer"}],
  recipes: [],
  selectedMapping: {},
  selectedRecipe: { id: null },
  selectedAtomicTransformation: null,
  selectedTransformation: null,
  dfasdls: {
    sources: null,
    target: null
  }
})

getSequenceParent = (currentNode) ->
  if(currentNode.parentNode != null && currentNode.parentNode.nodeName == 'seq')
    currentNode.parentNode
  else if (currentNode.parentNode != null)
    getSequenceParent(currentNode.parentNode)
  else
    undefined

Editor.CookbookresourcesResourcesController = Ember.Controller.extend({
  selectedSources: [],
  selectedTarget: null,
  name: '',
  nameError: false,
  duplicateError: false,
  getCookbook: -> this.get('model').cookbookresource.get('cookbook'),
})

Editor.CookbookresourcesSettingsController = Ember.Controller.extend({
  sourcesVersions: null,
  targetVersions: null,
})

Editor.CoolCheck = Ember.Checkbox.extend({
  hookup: (->
    action = this.get('action')
    if (action)
      this.on('change', this, this.sendHookup)

  ).on('init'),
  sendHookup: ((ev) ->
    action = this.get('action')
    controller = this.get('controller');
    #controller.send(action, this.$().prop('checked'))
  ),
  cleanup: (->
    this.off('change', this, this.sendHookup);
  ).on('willDestroyElement')
});

