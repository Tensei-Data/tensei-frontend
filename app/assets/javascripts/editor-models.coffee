###
  The basic models for the editor application.
  Some more helper models that are not used by Ember are defined in `EditorClasses`.
###

Editor.Cookbookresource = DS.Model.extend({
  cookbook: DS.attr(),
  ownerId: DS.attr('number'),
  groupId: DS.attr('number'),
  groupPermissions: DS.attr('number'),
  worldPermissions: DS.attr('number')
})

Editor.Dfasdlresource = DS.Model.extend({
  dfasdl: DS.attr(),
  ownerId: DS.attr('number'),
  groupId: DS.attr('number'),
  groupPermissions: DS.attr('number'),
  worldPermissions: DS.attr('number')
})
