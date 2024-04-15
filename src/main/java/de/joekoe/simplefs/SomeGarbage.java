package de.joekoe.simplefs

class SomeGarbage {
	private final String name;

	SomeGarbage(final String name) {
		this.name = name;
	}

	public String getName() { return this.name; }

	private void someUnusedMethod() {
		System.out.println("Hey, I am " + name);
	}
}
