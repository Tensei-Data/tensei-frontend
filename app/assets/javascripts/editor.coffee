###
  The main components for the editor application are kept here.
  Global initialisation, helpers and so on...
###

# Create main ember application.
window.Editor = Ember.Application.create({
#  LOG_TRANSITIONS: true,
#  LOG_TRANSITIONS_INTERNAL: true,
#  LOG_ACTIVE_GENERATION: true,
#  LOG_BINDINGS: true,
#  LOG_RESOLVER: true
})

Ember.onerror = (e) ->
  console.error("An error occured in " + e.fileName + ":" + e.lineNumber)
  console.error(e.message)
  console.error(e.stack)

# Remove the loading image.
Editor.RemoveLoadingImage = Ember.View.extend({
  didInsertElement: () ->
    $("#loadingImage").css("display", "none")
})

Ember.Handlebars.registerBoundHelper('trimClassNames', (passedString, options) ->
  transformerClassName = passedString
  splits = transformerClassName.split(".")
  if (splits.length > 0)
    transformerClassName = splits[splits.length - 1]
  new Handlebars.SafeString(transformerClassName)
)

Ember.Handlebars.registerBoundHelper('displayEmptyString', (passedString, options) ->
  if (passedString != null && passedString != undefined && passedString.length > 0)
    if (passedString == " ")
      new Handlebars.SafeString("␣")
    else
      new Handlebars.SafeString(passedString)
  else
    new Handlebars.SafeString("&empty;")
)

Ember.Handlebars.registerBoundHelper('transformMappingIDsOld', (passedString, options) ->
  idString = passedString
  if (idString.length > 0)
    idString = idString.join(", ")
  new Handlebars.SafeString(idString)
)

Ember.Handlebars.registerBoundHelper('displayTransformationNames', (transformations, options) ->
  names = ""
  if(transformations.length > 0)
    for transformer in transformations
      splits = transformer.transformerClassName.split(".")
      if (splits.length > 0)
        names =  names + splits[splits.length - 1] + ", "

    if (names.length > 0)
      names = names.substring(0, names.length - 2)

  new Handlebars.SafeString(names)
)

Ember.Handlebars.registerBoundHelper('displayMappingIDsIfPossible', (sourceIds, targetIds, recipe, options) ->
  if (sourceIds != undefined && sourceIds.length > 0)
    sourceIdString = (id.elementId for id in sourceIds)
  else
    sourceIdString = []
  if (targetIds != undefined && targetIds.length > 0)
    targetIdString = (id.elementId for id in targetIds)
  else
    targetIdString = []

  # Get the equal part of all IDs
  toDeleteSource = determineEqualPart(recipe, true)
  toDeleteTarget = determineEqualPart(recipe, false)

  # Concat the single source ids
  if (sourceIdString.length > 0)
    sourceIdString = sourceIdString.join(", ")
  # Concat the single target ids
  if (targetIdString.length > 0)
    targetIdString = targetIdString.join(", ")

  # Remove the equal part
  if (toDeleteSource != "" && toDeleteSource.indexOf("_") > -1)
    # We remove only parts that end with a "_"
    if(toDeleteSource.lastIndexOf("_") != toDeleteSource.length - 1)
      toDeleteSource = toDeleteSource.substring(0, toDeleteSource.lastIndexOf("_") + 1)
    sourceIdString = sourceIdString.replace(new RegExp(toDeleteSource, 'g'), "")

  if (toDeleteTarget != "" && toDeleteTarget.indexOf("_") > -1)
    # We remove only parts that end with a "_"
    if(toDeleteTarget.lastIndexOf("_") != toDeleteTarget.length - 1)
      toDeleteTarget = toDeleteTarget.substring(0, toDeleteTarget.lastIndexOf("_") + 1)
    targetIdString = targetIdString.replace(new RegExp(toDeleteTarget, 'g'), "")

  totalString = sourceIdString + " : " + targetIdString

  if (totalString.length <= 40)
    new Handlebars.SafeString(totalString)
  else
    ""
)

# We remove the equal part at the beginning of all ID names. That makes it easier to read the entries in the tooltip.
# @param ids    An array of object IDs
# @param recipe The current recipe where the IDs are mapped within a mapping
# @param source Whether the current ID array is from the source or the target IDs.
# @param options Array that can be used for additional hashed data.
# @return An array of IDs where parts of the names are removed that are equal at all entries.
Ember.Handlebars.registerBoundHelper('transformMappingIDs', (ids, recipe, source, options) ->
  if (ids != undefined)
    if (ids.elementId != undefined )
      idString = [ids.elementId]
    else
      idString = (id.elementId for id in ids)
  else
    idString = []

  # Get the equal part of all IDs
  toDelete = determineEqualPart(recipe, source)

  # Concat the single ids
  if (idString.length > 0)
    idString = idString.join(", ")

  # Remove the equal part
  if (toDelete != "" && toDelete.indexOf("_") > -1)
    # We remove only parts that end with a "_"
    if(toDelete.lastIndexOf("_") != toDelete.length - 1)
      toDelete = toDelete.substring(0, toDelete.lastIndexOf("_") + 1)
    idString = idString.replace(new RegExp(toDelete, 'g'), "")

  new Handlebars.SafeString(idString)
)

# Get the source or target IDs and determine the part that must be removed.
# @param recipe The actual recipe.
# @param source Whether the source or target IDs are important.
# @return The part that should be removed.
determineEqualPart = (recipe, source) ->
  ids = []
  if(source)
    for value in recipe.mappings
      for obj in value.sources
        ids = ids.concat obj.elementId
  else
    for value in recipe.mappings
      for obj in value.targets
        ids = ids.concat obj.elementId
  removablePart(ids.unique())

# Determine the part of multiple ID names that is equal at the
# beginning of the IDs. e.g. [id_row_name, id_row_title] -> 'id_row_]
# @param uniqueEntries Array with names
# @return The equal part in the names
removablePart = (uniqueEntries) ->
  if (uniqueEntries.length > 1)
    equal = true
    toRemove = ""
    pos = 0
    while(equal)
      pos = pos + 1
      toRemove = uniqueEntries[0].substring(0,pos)
      for entry in uniqueEntries
        if(entry.substring(0,pos) != toRemove)
          equal = false
          break
    if(pos > 1)
      uniqueEntries[0].substring(0,pos-1)
    else
      ""
  else
    ""

# Helper function that returns an array with uniqe entries.
Array::unique = ->
  output = {}
  output[@[key]] = @[key] for key in [0...@length]
  value for key, value of output

# Choose the data adapter.
Editor.ApplicationAdapter = DS.RESTAdapter.extend()

###
  Some global helper functions.
###
window.EditorHelpers = {
  findDfasdlResource: (id, dfasdlresources) ->
    filtered = dfasdlresources.filter (r) ->
      dfasdl = r.get('dfasdl')
      dfasdl.id == id
    if (filtered.length == 0)
      null
    else
      filtered[0]

  findDfasdlResources: (dfasdls, dfasdlresources) ->
    this.findDfasdlResource(dfasdl.id, dfasdlresources) for dfasdl in dfasdls

  getDFASDLIds: (dfasdlId, dfasdl, keys) ->
    ids = dfasdl.match(/<(?!choice|celem|cid|elem|fixseq|ref|seq).*?id="([-\._\w]+)"/g)
    if(ids)
      ausdruck = /<(?!choice|celem|cid|elem|fixseq|ref|seq).*?id="([-\._\w]+)"/
      ids.forEach((id) ->
        ergebnis = ausdruck.exec(id)
        if(ergebnis)
          keys.push({elementId: ergebnis[1], dfasdlId: dfasdlId, title: ergebnis[1] + " ( " + dfasdlId + " )"})
      )
    keys

  getDFASDLIdsSequences: (dfasdl, keys) ->
    ids = dfasdl.match(/<seq .*?id="([-\._\w]+)"/g)
    if(ids)
      ausdruck = /<seq .*?id="([-\._\w]+?)"/
      ids.forEach((id) ->
        ergebnis = ausdruck.exec(id)
        if(ergebnis)
          if(!keys.has(ergebnis[1]))
            keys.add(ergebnis[1])
      )
    keys

  # A wrapper for custom ajax requests.
  # Usage: `model: -> ajax('my url', options)`
  ajax: (url, options) ->
    new Ember.RSVP.Promise((resolve, reject) ->
      options = options || {}
      options.url = url

      options.success = (data) ->
        Ember.run(null, resolve, data)

      options.error = (jqxhr, status, something) ->
        Ember.run(null, reject, arguments)

      Ember.$.ajax(options)
    )

  cookbookExists: (name, id) ->
    EditorHelpers.ajax('/cookbookresources/exists/' + name + '/' + id, { dataType: 'json'}).then((value) -> value)

  # Returns a hash for a dfasdl that containt it's id, versions and the currently active version.
  loadDfasdlVersions: (dfasdl, dfasdlresources) ->
    target = EditorHelpers.findDfasdlResource(dfasdl.id, dfasdlresources)
    EditorHelpers.ajax('/dfasdlresources/' + target.id + '/versions', { dataType: 'json' }).then((value) -> {
      id: dfasdl.id,
      selected: dfasdl.version,
      versions: value
    })

  loadDfasdl: (resource_id, version) ->
    EditorHelpers.ajax('/dfasdlresources/' + resource_id + '/versions/' + version, { dataType: 'json'}).then((value) -> value)

  requestAdvancedSemanticSuggest: (cookbookId) ->
    route = jsRoutes.controllers.CookbookResourcesController.suggestMappings(cookbookId, "AdvancedSemantics")
    options = {
      dataType: "json",
      method: route.method,
      url: route.url
    }
    EditorHelpers.ajax(route.url, options).then((value) -> value)

  requestSimpleSuggest: (cookbookId) ->
    route = jsRoutes.controllers.CookbookResourcesController.suggestMappings(cookbookId, "Simple")
    options = {
      dataType: "json",
      method: route.method,
      url: route.url
    }
    EditorHelpers.ajax(route.url, options).then((value) -> value)

  requestSemanticSuggest: (cookbookId) ->
    route = jsRoutes.controllers.CookbookResourcesController.suggestMappings(cookbookId, "SimpleSemantics")
    options = {
      dataType: "json",
      method: route.method,
      url: route.url
    }
    EditorHelpers.ajax(route.url, options).then((value) -> value)

  # Load all existing versions of a dfasdl into the field of the given controller.
  setDfasdlVersions: (controller, targetFieldName, dfasdl, dfasdlresources) ->
    target = EditorHelpers.findDfasdlResource(dfasdl.id, dfasdlresources)
    EditorHelpers.ajax('/dfasdlresources/' + target.id + '/versions', { dataType: 'json' }).then(
      (value) ->
        controller.set(targetFieldName, value)
      ,
      (reason) ->
        controller.set(targetFieldName, [])
        console.error(reason)
    )

  # Helper function that returns the entry of objects array
  # If no object with the specified 'name' and 'dfasdlId' exists, the returned
  # value is 'undefined'.
  elementExistsInObject: (name, dfasdlId, objects) ->
    (i for i in objects when i.name is name and i.dfasdlId is dfasdlId)[0]

  elementExistsInElementReferences: (elementId, dfasdlId, elementReferences) ->
    (i for i in elementReferences when i.elementId is elementId and i.dfasdlId is dfasdlId)[0]
}

###
  A set of several "constructor" functions for helper classes.
###
window.EditorClasses = {
  createDfasdl: (id, content, version) ->
    {
      type: 'DFASDL',
      id: id,
      content: content,
      version: version
    }

  createMappingKeyFieldDefinition: (name) ->
    {
      type: 'MappingKeyFieldDefinition',
      name: name
    }

  createAtomicTransformationDescription: (elementRef, transformerClassName, options) ->
    {
      type: 'AtomicTransformationDescription',
      element: elementRef,
      transformerClassName: transformerClassName,
      options: options
    }

  createTransformerOption: (srcType, dstType, params) ->
    {
      type: 'TransformerOptions',
      srcType: srcType,
      dstType: dstType,
      params: params
    }
    
  createTransformationDescription: (transformerClassName, options) ->
    {
      type: 'TransformationDescription',
      transformerClassName: transformerClassName,
      options: options
    }

  createMappingTransformation: (sources, targets, transformations, atomicTransformations, mappingKey) ->
    {
      type: 'MappingTransformation',
      sources: sources,
      targets: targets,
      transformations: transformations,
      atomicTransformations: atomicTransformations,
      mappingKey: mappingKey
    }

  createElementReference: (dfasdlId, elementId) ->
    {
      type: 'ElementReference',
      dfasdlId: dfasdlId,
      elementId: elementId
    }

  createRecipe: (id, mode, mappings) ->
    {
      type: 'Recipe',
      id: id,
      mode: mode,
      mappings: mappings
    }

  createCookbook: (id, sources, target, recipes) ->
    {
      type: 'Cookbook',
      id: id,
      sources: sources,
      target: target,
      recipes: recipes
    }

  createElementToDisplay: (name, dfasdlId, selected) ->
    {
      name: name,
      dfasdlId: dfasdlId,
      selected: selected
    }
}
