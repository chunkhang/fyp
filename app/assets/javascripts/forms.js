/* globals $, toastr */

export function forms() {

  // Material select
  $(".mdb-select").material_select();

  // Material time picker
  var timePickers = $(".timepicker");
  timePickers.each(function() {
    var defaultTime = $(this).val();
    $(this).pickatime({
      twelvehour: true,
      default: defaultTime === "" ? "08:00AM" : defaultTime
    });
  });

  // Material autocomplete
  var autocomplete = $("#venue");
  var venues = $("venueSelection");
  var venueSelections = venues.map(function() {
    return $(this).text();
  }).get();
  autocomplete.mdb_autocomplete({
    data: venueSelections
  });
  autocomplete.click(function() {
    window.scrollTo(0,document.body.scrollHeight);
  });

  // Add invalid classes to text inputs
  var inputs = $("input");
  inputs.each(function() {
    if ($(this).siblings("invalid").length == 1) {
      $(this).addClass("invalid");
    }
  });

  // Constraint errors
  var errors = $("error");
  if (errors.length != 0) {
    errors.each(function() {
      var error = $(this).text();
      toastr.error(error);
      if (error.indexOf("venue") != -1) {
        autocomplete.addClass("invalid");
      } else {
        timePickers.each(function() {
          $(this).addClass("invalid");
        });
      }
    });
  }

}
