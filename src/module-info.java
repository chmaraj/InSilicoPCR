module InSilicoPCR {
	requires java.desktop;
	requires commons.cli;
	requires transitive javafx.base;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires java.base;

	exports insilicopcr;
	exports commandpcr;
	exports dispatchpcr;
}