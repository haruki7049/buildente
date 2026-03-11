{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-compat.url = "github:edolstra/flake-compat";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      imports = [
        inputs.treefmt-nix.flakeModule
      ];

      perSystem =
        { pkgs, lib, ... }:
        {
          treefmt = {
            projectRootFile = ".git/config";

            # Nix
            programs.nixfmt.enable = true;

            # Java
            programs.google-java-format.enable = true;

            # kotlin for Gradle
            programs.ktlint.enable = true;

            # XML
            programs.xmllint.enable = true;

            # Markdown
            programs.mdformat.enable = true;

            # ShellScript
            programs.shellcheck.enable = true;
            programs.shfmt.enable = true;
          };

          devShells.default = pkgs.mkShell {
            nativeBuildInputs = [
              # Runtime & Builder
              pkgs.jdk25
              pkgs.gradle_9

              # LSP
              pkgs.nil # Nix
              # pkgs.jdt-language-server # Java
            ];
          };
        };
    };
}
