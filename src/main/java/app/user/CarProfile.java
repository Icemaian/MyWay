package app.user;

public record CarProfile(
  int id, String nickname, String make, String model,
  Integer year, Double heightM, Double weightKg
) {}

