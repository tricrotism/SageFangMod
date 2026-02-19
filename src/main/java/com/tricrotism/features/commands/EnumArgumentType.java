package com.tricrotism.features.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier argument type for any Java enum.
 * Adapted from KhaoDoesDev's implementation for Mojmap.
 */
public class EnumArgumentType<T extends Enum<T>> implements ArgumentType<T> {
    private static final DynamicCommandExceptionType NO_SUCH_VALUE = new DynamicCommandExceptionType(value ->
            Component.literal(value + " is not a valid argument."));
    private final T[] values;

    public EnumArgumentType(T defaultValue) {
        this.values = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public static <T extends Enum<T>> EnumArgumentType<T> enumArgument(T defaultValue) {
        return new EnumArgumentType<>(defaultValue);
    }

    @Override
    public T parse(StringReader reader) throws CommandSyntaxException {
        String argument = reader.readUnquotedString();
        return Arrays.stream(values)
                .filter(value -> value.name().equalsIgnoreCase(argument))
                .findFirst()
                .orElseThrow(() -> NO_SUCH_VALUE.create(argument));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(values).map(Enum::name), builder);
    }
}
