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

  // Material datepicker
  var datePicker = $(".datepicker");
  if ($("#calendar").length != 0) {
    $("#event-modal-replace").click(function() {
      var databaseDate = $("#event-modal").data("databaseDate");
      var originalDate = new Date(
        parseInt(databaseDate.substring(0, 4)),
        parseInt(databaseDate.substring(5, 7)) - 1,
        parseInt(databaseDate.substring(8, 10)) + 1
      );
      var fromInput = $("#event-modal-from").pickadate({
        format: "yyyy-mm-dd",
        min: originalDate,
        closeOnSelect: false,
        closeOnClear: false,
        today: "",
        clear: "",
        container: "body"
      });
      var fromPicker = fromInput.pickadate("picker");
      var toInput = $("#event-modal-to").pickadate({
        format: "yyyy-mm-dd",
        min: originalDate,
        closeOnSelect: false,
        closeOnClear: false,
        today: "",
        clear: "",
        container: "body"
      });
      var toPicker = toInput.pickadate("picker");
      // Set minimum and maximum of pickers accordingly
      fromPicker.on("set", function(event) {
        if (event.select) {
          toPicker.set("min", fromPicker.get("select"));
        }
      });
      toPicker.on("set", function(event) {
        if (event.select) {
          fromPicker.set("max", toPicker.get("select"));
        }
      });
      // Clear pickers
      fromPicker.clear();
      toPicker.clear();
      fromPicker.set("min", originalDate);
      toPicker.set("min", originalDate);
      fromPicker.set("max", false);
    });
  } else {
    var startDate = $("#start-date");
    var minimumDate = new Date(
      parseInt(startDate.text().substring(0, 4)),
      parseInt(startDate.text().substring(5, 7)) - 1,
      parseInt(startDate.text().substring(8, 10)) + 1
    );
    datePicker.pickadate({
      format: "yyyy-mm-dd",
      min: minimumDate,
      closeOnSelect: false,
      closeOnClear: false,
      today: "",
      clear: ""
    });
  }

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
        datePicker.addClass("invalid");
      }
    });
  }

}
