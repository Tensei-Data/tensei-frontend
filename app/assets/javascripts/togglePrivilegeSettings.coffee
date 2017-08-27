$ ->
  togglePrivileges = (state) ->
    if (state == "public")
      $("#privilegeSettings").hide()
    else
      $("#privilegeSettings").show()

  $("select#resourceAccessType").change -> togglePrivileges($("#resourceAccessType").val())

  $("select#resourceAccessType").trigger("change")

